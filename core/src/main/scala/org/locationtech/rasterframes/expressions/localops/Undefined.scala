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

package org.locationtech.rasterframes.expressions.localops

import geotrellis.raster.Tile
import org.apache.spark.sql.Column
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionDescription}
import org.locationtech.rasterframes.expressions.{NullToValue, UnaryRasterOp}

@ExpressionDescription(
  usage = "_FUNC_(tile) - Return a tile with ones where the input is NoData, otherwise zero.",
  arguments = """
  Arguments:
    * tile - tile column to inspect""",
  examples = """
  Examples:
    > SELECT  _FUNC_(tile);
       ..."""
)
case class Undefined(child: Expression) extends UnaryRasterOp with NullToValue with CodegenFallback {
  override def nodeName: String = "rf_local_no_data"
  def na: Any = null
  protected def op(child: Tile): Tile = child.localUndefined()
  def withNewChildInternal(newChild: Expression): Expression = copy(newChild)
}
object Undefined {
  def apply(tile: Column): Column = new Column(Undefined(tile.expr))
}
