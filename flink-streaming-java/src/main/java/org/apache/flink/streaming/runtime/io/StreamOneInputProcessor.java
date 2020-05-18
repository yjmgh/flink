/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.runtime.io;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.runtime.io.disk.iomanager.IOManager;
import org.apache.flink.runtime.io.network.partition.consumer.InputGate;
import org.apache.flink.runtime.metrics.groups.OperatorMetricGroup;
import org.apache.flink.runtime.metrics.groups.TaskIOMetricGroup;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.metrics.WatermarkGauge;
import org.apache.flink.streaming.runtime.streamrecord.StreamElement;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.streamstatus.StatusWatermarkValve;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatus;
import org.apache.flink.streaming.runtime.streamstatus.StreamStatusMaintainer;
import org.apache.flink.streaming.runtime.tasks.OperatorChain;
import org.apache.flink.streaming.runtime.tasks.StreamTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkNotNull;
import static org.apache.flink.util.Preconditions.checkState;

/**
 * Input reader for {@link org.apache.flink.streaming.runtime.tasks.OneInputStreamTask}.
 *
 * <p>This internally uses a {@link StatusWatermarkValve} to keep track of {@link Watermark} and
 * {@link StreamStatus} events, and forwards them to event subscribers once the
 * {@link StatusWatermarkValve} determines the {@link Watermark} from all inputs has advanced, or
 * that a {@link StreamStatus} needs to be propagated downstream to denote a status change.
 *
 * <p>Forwarding elements, watermarks, or status status elements must be protected by synchronizing
 * on the given lock object. This ensures that we don't call methods on a
 * {@link OneInputStreamOperator} concurrently with the timer callback or other things.
 *
 * @param <IN> The type of the record that can be read with this record reader.
 */
@Internal
public final class StreamOneInputProcessor<IN> implements StreamInputProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(StreamOneInputProcessor.class);

	private final StreamTaskInput input;

	private final Object lock;

	private final OperatorChain<?, ?> operatorChain;

	// ---------------- Status and Watermark Valve ------------------

	/** Valve that controls how watermarks and stream statuses are forwarded. */
	private StatusWatermarkValve statusWatermarkValve;

	private final StreamStatusMaintainer streamStatusMaintainer;

	private final OneInputStreamOperator<IN, ?> streamOperator;

	// ---------------- Metrics ------------------

	private final WatermarkGauge watermarkGauge;
	private Counter numRecordsIn;

	@SuppressWarnings("unchecked")
	public StreamOneInputProcessor(
			InputGate[] inputGates,
			TypeSerializer<IN> inputSerializer,
			StreamTask<?, ?> checkpointedTask,
			CheckpointingMode checkpointMode,
			Object lock,
			IOManager ioManager,
			Configuration taskManagerConfig,
			StreamStatusMaintainer streamStatusMaintainer,
			OneInputStreamOperator<IN, ?> streamOperator,
			TaskIOMetricGroup metrics,
			WatermarkGauge watermarkGauge,
			String taskName,
			OperatorChain<?, ?> operatorChain) throws IOException {

		InputGate inputGate = InputGateUtil.createInputGate(inputGates);

		CheckpointedInputGate barrierHandler = InputProcessorUtil.createCheckpointedInputGate(
			checkpointedTask,
			checkpointMode,
			ioManager,
			inputGate,
			taskManagerConfig,
			taskName);
		this.input = new StreamTaskNetworkInput(barrierHandler, inputSerializer, ioManager, 0);

		this.lock = checkNotNull(lock);

		this.streamStatusMaintainer = checkNotNull(streamStatusMaintainer);
		this.streamOperator = checkNotNull(streamOperator);

		this.statusWatermarkValve = new StatusWatermarkValve(
			inputGate.getNumberOfInputChannels(),
			new ForwardingValveOutputHandler(streamOperator, lock));

		this.watermarkGauge = watermarkGauge;
		metrics.gauge("checkpointAlignmentTime", barrierHandler::getAlignmentDurationNanos);

		this.operatorChain = checkNotNull(operatorChain);
	}

	@Override
	public boolean processInput() throws Exception {
		// 初始化 NumRecordsIn 的 Metric
		initializeNumRecordsIn();

		// 从 input 中获取数据，注意如果input 中没有数据，则不能阻塞
		StreamElement recordOrMark = input.pollNextNullable();
		if (recordOrMark == null) {
			input.isAvailable().get();
			return !checkFinished();
		}
		// 从 input 中获取 channel 的编号，然后执行 processElement 方法
		int channel = input.getLastChannel();
		checkState(channel != StreamTaskInput.UNSPECIFIED);

		// processElement 方法中判断 element 的类型，
		// 四种类型：Record、WaterMark、StreamStatus、LatencyMarker
		processElement(recordOrMark, channel);
		return true;
	}

	private boolean checkFinished() throws Exception {
		boolean isFinished = input.isFinished();
		if (isFinished) {
			synchronized (lock) {
				operatorChain.endInput(1);
			}
		}
		return isFinished;
	}

	private void processElement(StreamElement recordOrMark, int channel) throws Exception {
		if (recordOrMark.isRecord()) {
			// now we can do the actual processing
			StreamRecord<IN> record = recordOrMark.asRecord();
			synchronized (lock) {
				numRecordsIn.inc();
				// 因为这里是 StreamOneInputProcessor，所以会调用 OneInputStreamOperator 的相关方法
				// 首先调用 OneInputStreamOperator 的 setKeyContextElement1
				// 该操作主要是要调用 KeyedStateBackend 的 setCurrentKey 方法，
				// 然后处理该数据时，从 KeyedStateBackend 中读取到的 状态数据都是当前 key 的数据
				streamOperator.setKeyContextElement1(record);

				//然后调用 OneInputStreamOperator 的 processElement 方法
				//实际上执行具体算子的 processElement 方法，
				// 例如 StreamFlatMap、StreamFilter 的 processElement 方法，
				// StreamFlatMap 就会调用 FlatmapFunction 的 flatmap 方法
				streamOperator.processElement(record);
			}
		}
		else if (recordOrMark.isWatermark()) {
			// handle watermark
			statusWatermarkValve.inputWatermark(recordOrMark.asWatermark(), channel);
		} else if (recordOrMark.isStreamStatus()) {
			// handle stream status
			statusWatermarkValve.inputStreamStatus(recordOrMark.asStreamStatus(), channel);
		} else if (recordOrMark.isLatencyMarker()) {
			// handle latency marker
			synchronized (lock) {
				streamOperator.processLatencyMarker(recordOrMark.asLatencyMarker());
			}
		} else {
			throw new UnsupportedOperationException("Unknown type of StreamElement");
		}
	}

	private void initializeNumRecordsIn() {
		if (numRecordsIn == null) {
			try {
				numRecordsIn = ((OperatorMetricGroup) streamOperator.getMetricGroup()).getIOMetricGroup().getNumRecordsInCounter();
			} catch (Exception e) {
				LOG.warn("An exception occurred during the metrics setup.", e);
				numRecordsIn = new SimpleCounter();
			}
		}
	}

	@Override
	public void close() throws IOException {
		input.close();
	}

	private class ForwardingValveOutputHandler implements StatusWatermarkValve.ValveOutputHandler {
		private final OneInputStreamOperator<IN, ?> operator;
		private final Object lock;

		private ForwardingValveOutputHandler(final OneInputStreamOperator<IN, ?> operator, final Object lock) {
			this.operator = checkNotNull(operator);
			this.lock = checkNotNull(lock);
		}

		@Override
		public void handleWatermark(Watermark watermark) {
			try {
				synchronized (lock) {
					watermarkGauge.setCurrentWatermark(watermark.getTimestamp());
					operator.processWatermark(watermark);
				}
			} catch (Exception e) {
				throw new RuntimeException("Exception occurred while processing valve output watermark: ", e);
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public void handleStreamStatus(StreamStatus streamStatus) {
			try {
				synchronized (lock) {
					streamStatusMaintainer.toggleStreamStatus(streamStatus);
				}
			} catch (Exception e) {
				throw new RuntimeException("Exception occurred while processing valve output stream status: ", e);
			}
		}
	}
}