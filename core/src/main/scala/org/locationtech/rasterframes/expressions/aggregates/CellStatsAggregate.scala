/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2019 Astraea, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.locationtech.rasterframes.expressions.aggregates

import org.locationtech.rasterframes._
import org.locationtech.rasterframes.expressions.accessors.ExtractTile
import org.locationtech.rasterframes.stats.CellStatistics
import geotrellis.raster._
import org.apache.spark.sql.catalyst.expressions.aggregate.{AggregateExpression, AggregateFunction, AggregateMode, Complete}
import org.apache.spark.sql.catalyst.expressions.{ExprId, Expression, ExpressionDescription, NamedExpression}
import org.apache.spark.sql.execution.aggregate.ScalaUDAF
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types.{DataType, _}
import org.apache.spark.sql.{Column, Row, TypedColumn}

/**
 * Statistics aggregation function for a full column of tiles.
 *
 * @since 4/17/17
 */
case class CellStatsAggregate() extends UserDefinedAggregateFunction {
  import CellStatsAggregate.C
  // TODO: rewrite as a DeclarativeAggregate
  def inputSchema: StructType = StructType(StructField("value", tileUDT) :: Nil)

  def dataType: DataType = StructType(Seq(
    StructField("data_cells", LongType),
    StructField("no_data_cells", LongType),
    StructField("min", DoubleType),
    StructField("max", DoubleType),
    StructField("mean", DoubleType),
    StructField("variance", DoubleType)
  ))

  def bufferSchema: StructType = StructType(Seq(
    StructField("data_cells", LongType),
    StructField("no_data_cells", LongType),
    StructField("min", DoubleType),
    StructField("max", DoubleType),
    StructField("sum", DoubleType),
    StructField("sumSqr", DoubleType)
  ))

  def deterministic: Boolean = true

  def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer(C.COUNT) = 0L
    buffer(C.NODATA) = 0L
    buffer(C.MIN) = Double.MaxValue
    buffer(C.MAX) = Double.MinValue
    buffer(C.SUM) = 0.0
    buffer(C.SUM_SQRS) = 0.0
  }

  def update(buffer: MutableAggregationBuffer, input: Row): Unit =
    if (!input.isNullAt(0)) {
      val tile = input.getAs[Tile](0)
      var count = buffer.getLong(C.COUNT)
      var nodata = buffer.getLong(C.NODATA)
      var min = buffer.getDouble(C.MIN)
      var max = buffer.getDouble(C.MAX)
      var sum = buffer.getDouble(C.SUM)
      var sumSqr = buffer.getDouble(C.SUM_SQRS)

      tile.foreachDouble(
          c =>
            if (isData(c)) {
            count += 1
            min = math.min(min, c)
            max = math.max(max, c)
            sum = sum + c
            sumSqr = sumSqr + c * c
          } else nodata += 1)

      buffer(C.COUNT) = count
      buffer(C.NODATA) = nodata
      buffer(C.MIN) = min
      buffer(C.MAX) = max
      buffer(C.SUM) = sum
      buffer(C.SUM_SQRS) = sumSqr
    }

  def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {
    buffer1(C.COUNT) = buffer1.getLong(C.COUNT) + buffer2.getLong(C.COUNT)
    buffer1(C.NODATA) = buffer1.getLong(C.NODATA) + buffer2.getLong(C.NODATA)
    buffer1(C.MIN) = math.min(buffer1.getDouble(C.MIN), buffer2.getDouble(C.MIN))
    buffer1(C.MAX) = math.max(buffer1.getDouble(C.MAX), buffer2.getDouble(C.MAX))
    buffer1(C.SUM) = buffer1.getDouble(C.SUM) + buffer2.getDouble(C.SUM)
    buffer1(C.SUM_SQRS) = buffer1.getDouble(C.SUM_SQRS) + buffer2.getDouble(C.SUM_SQRS)
  }

  def evaluate(buffer: Row): Any = {
    val count = buffer.getLong(C.COUNT)
    val sum = buffer.getDouble(C.SUM)
    val sumSqr = buffer.getDouble(C.SUM_SQRS)
    val mean = sum / count
    val variance = sumSqr / count - mean * mean
    Row(count, buffer(C.NODATA), buffer(C.MIN), buffer(C.MAX), mean, variance)
  }
}

object CellStatsAggregate {
  def apply(col: Column): TypedColumn[Any, CellStatistics] =
    new CellStatsAggregate()(ExtractTile(col))
      .as(s"rf_agg_stats($col)")
      .as[CellStatistics]

  /** Adapter hack to allow UserDefinedAggregateFunction to be referenced as an expression. */
  @ExpressionDescription(
    usage = "_FUNC_(tile) - Compute aggregate descriptive cell statistics over a tile column.",
    arguments = """
  Arguments:
    * tile - tile column to analyze""",
    examples = """
  Examples:
    > SELECT _FUNC_(tile);
    +----------+-------------+---+-----+-------+-----------------+
    |data_cells|no_data_cells|min|max  |mean   |variance         |
    +----------+-------------+---+-----+-------+-----------------+
    |960       |40           |1.0|255.0|127.175|5441.704791666667|
    +----------+-------------+---+-----+-------+-----------------+"""
  )
  class CellStatsAggregateUDAF(aggregateFunction: AggregateFunction, mode: AggregateMode, isDistinct: Boolean, filter: Option[Expression], resultId: ExprId)
    extends AggregateExpression(aggregateFunction, mode, isDistinct, filter, resultId) {
    def this(child: Expression) = this(ScalaUDAF(Seq(ExtractTile(child)), new CellStatsAggregate()), Complete, false, None, NamedExpression.newExprId)
    override def nodeName: String = "rf_agg_stats"
  }
  object CellStatsAggregateUDAF {
    def apply(child: Expression): CellStatsAggregateUDAF = new CellStatsAggregateUDAF(child)
  }

  /**  Column index values. */
  private object C {
    final val COUNT = 0
    final val NODATA = 1
    final val MIN = 2
    final val MAX = 3
    final val SUM = 4
    final val SUM_SQRS = 5
  }
}
