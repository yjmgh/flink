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

package org.apache.flink.table.planner.functions.aggfunctions;

import org.apache.flink.table.dataformat.BinaryString;
import org.apache.flink.table.dataformat.Decimal;
import org.apache.flink.table.dataformat.GenericRow;
import org.apache.flink.table.functions.AggregateFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.BooleanFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.ByteFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.DecimalFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.DoubleFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.FloatFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.IntFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.LongFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.ShortFirstValueAggFunction;
import org.apache.flink.table.planner.functions.aggfunctions.FirstValueAggFunction.StringFirstValueAggFunction;
import org.apache.flink.table.runtime.typeutils.DecimalTypeInfo;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Test case for built-in FirstValue aggregate function.
 * This class tests `accumulate` method with order argument.
 */
@RunWith(Parameterized.class)
public class FirstValueAggFunctionWithOrderTest<T> extends FirstLastValueAggFunctionWithOrderTestBase<T> {

	@Parameterized.Parameter
	public AggFunctionWithOrderTestSpec<T> aggFunctionTestSpec;

	private static final int DECIMAL_PRECISION = 20;
	private static final int DECIMAL_SCALE = 6;

	@Override
	protected List<List<Long>> getInputOrderSets() {
		return aggFunctionTestSpec.inputOrderSets;
	}

	@Override
	protected List<List<T>> getInputValueSets() {
		return aggFunctionTestSpec.inputValueSets;
	}

	@Override
	protected List<T> getExpectedResults() {
		return aggFunctionTestSpec.expectedResults;
	}

	@Override
	protected AggregateFunction<T, GenericRow> getAggregator() {
		return aggFunctionTestSpec.aggregator;
	}

	@Parameterized.Parameters(name = "{index}: {0}")
	public static List<AggFunctionTestSpec> testData() {
		return Arrays.asList(
				/**
				 * Test for ByteFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new ByteFirstValueAggFunction(),
						numberInputOrderSets(),
						numberInputValueSets(Byte::valueOf),
						numberExpectedResults(Byte::valueOf)
				),
				/**
				 * Test for ShortFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new ShortFirstValueAggFunction(),
						numberInputOrderSets(),
						numberInputValueSets(Short::valueOf),
						numberExpectedResults(Short::valueOf)
				),
				/**
				 * Test for IntFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new IntFirstValueAggFunction(),
						numberInputOrderSets(),
						numberInputValueSets(Integer::valueOf),
						numberExpectedResults(Integer::valueOf)
				),
				/**
				 * Test for LongFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new LongFirstValueAggFunction(),
						numberInputOrderSets(),
						numberInputValueSets(Long::valueOf),
						numberExpectedResults(Long::valueOf)
				),
				/**
				 * Test for FloatFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new FloatFirstValueAggFunction(),
						numberInputOrderSets(),
						numberInputValueSets(Float::valueOf),
						numberExpectedResults(Float::valueOf)
				),
				/**
				 * Test for DoubleFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new DoubleFirstValueAggFunction(),
						numberInputOrderSets(),
						numberInputValueSets(Double::valueOf),
						numberExpectedResults(Double::valueOf)
				),
				/**
				 * Test for BooleanFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new BooleanFirstValueAggFunction(),
						Arrays.asList(
								Arrays.asList(
										6L,
										2L,
										3L
								),
								Arrays.asList(
										1L,
										2L,
										3L
								),
								Arrays.asList(
										10L,
										2L,
										5L,
										11L,
										3L,
										7L,
										5L
								),
								Arrays.asList(
										6L,
										9L,
										5L
								),
								Arrays.asList(
										4L,
										3L
								)
						),
						Arrays.asList(
								Arrays.asList(
										false,
										false,
										false
								),
								Arrays.asList(
										true,
										true,
										true
								),
								Arrays.asList(
										true,
										false,
										null,
										true,
										false,
										true,
										null
								),
								Arrays.asList(
										null,
										null,
										null
								),
								Arrays.asList(
										null,
										true
								)
						),
						Arrays.asList(
								false,
								true,
								false,
								null,
								true
						)
				),
				/**
				 * Test for DecimalFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new DecimalFirstValueAggFunction(DecimalTypeInfo.of(DECIMAL_PRECISION, DECIMAL_SCALE)),
						Arrays.asList(
								Arrays.asList(
										10L,
										2L,
										1L,
										5L,
										null,
										3L,
										1L,
										5L,
										2L
								),
								Arrays.asList(
										6L,
										5L,
										null,
										8L,
										null
								),
								Arrays.asList(
										8L,
										6L
								)
						),
						Arrays.asList(
								Arrays.asList(
										Decimal.castFrom("1", DECIMAL_PRECISION, DECIMAL_SCALE),
										Decimal.castFrom("1000.000001", DECIMAL_PRECISION, DECIMAL_SCALE),
										Decimal.castFrom("-1", DECIMAL_PRECISION, DECIMAL_SCALE),
										Decimal.castFrom("-999.998999", DECIMAL_PRECISION, DECIMAL_SCALE),
										null,
										Decimal.castFrom("0", DECIMAL_PRECISION, DECIMAL_SCALE),
										Decimal.castFrom("-999.999", DECIMAL_PRECISION, DECIMAL_SCALE),
										null,
										Decimal.castFrom("999.999", DECIMAL_PRECISION, DECIMAL_SCALE)
								),
								Arrays.asList(
										null,
										null,
										null,
										null,
										null
								),
								Arrays.asList(
										null,
										Decimal.castFrom("0", DECIMAL_PRECISION, DECIMAL_SCALE)
								)
						),
						Arrays.asList(
								Decimal.castFrom("-1", DECIMAL_PRECISION, DECIMAL_SCALE),
								null,
								Decimal.castFrom("0", DECIMAL_PRECISION, DECIMAL_SCALE)
						)
				),
				/**
				 * Test for StringFirstValueAggFunction.
				 */
				new AggFunctionWithOrderTestSpec<>(
						new StringFirstValueAggFunction(),
						Arrays.asList(
								Arrays.asList(
										10L,
										2L,
										5L,
										null,
										3L,
										1L,
										5L
								),
								Arrays.asList(
										6L,
										5L
								),
								Arrays.asList(
										8L,
										6L
								),
								Arrays.asList(
										6L,
										4L,
										3L
								)
						),
						Arrays.asList(
								Arrays.asList(
										BinaryString.fromString("abc"),
										BinaryString.fromString("def"),
										BinaryString.fromString("ghi"),
										null,
										BinaryString.fromString("jkl"),
										null,
										BinaryString.fromString("zzz")
								),
								Arrays.asList(
										null,
										null
								),
								Arrays.asList(
										null,
										BinaryString.fromString("a")
								),
								Arrays.asList(
										BinaryString.fromString("x"),
										null,
										BinaryString.fromString("e")
								)
						),
						Arrays.asList(
								BinaryString.fromString("def"),
								null,
								BinaryString.fromString("a"),
								BinaryString.fromString("e")
						)
				)
				);
	}

	private static List<List<Long>> numberInputOrderSets() {
		return Arrays.asList(
				Arrays.asList(
						10L,
						2L,
						5L,
						6L,
						11L,
						3L,
						7L,
						5L
				),
				Arrays.asList(
						8L,
						6L,
						9L,
						5L
				),
				Arrays.asList(
						null,
						6L,
						4L,
						3L
				)
		);
	}

	private static <N> List<List<N>> numberInputValueSets(Function<String, N> strToValueFun) {
		return Arrays.asList(
				Arrays.asList(
						strToValueFun.apply("1"),
						null,
						strToValueFun.apply("-99"),
						strToValueFun.apply("3"),
						null,
						strToValueFun.apply("3"),
						strToValueFun.apply("2"),
						strToValueFun.apply("-99")
				),
				Arrays.asList(
						null,
						null,
						null,
						null
				),
				Arrays.asList(
						null,
						strToValueFun.apply("10"),
						null,
						strToValueFun.apply("5")
				)
		);
	}

	private static <N> List<N> numberExpectedResults(Function<String, N> strToValueFun) {
		return Arrays.asList(
				strToValueFun.apply("3"),
				null,
				strToValueFun.apply("5")
		);
	}
}
