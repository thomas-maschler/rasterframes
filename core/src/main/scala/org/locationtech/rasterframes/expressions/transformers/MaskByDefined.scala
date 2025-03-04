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

package org.locationtech.rasterframes.expressions.transformers
import geotrellis.raster.{NODATA, Tile, isNoData}
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.{Column, TypedColumn}
import org.apache.spark.sql.catalyst.expressions.{BinaryExpression, Expression, ExpressionDescription}
import org.locationtech.rasterframes.expressions.{RasterResult, row}
import org.locationtech.rasterframes.tileEncoder


@ExpressionDescription(
  usage = "_FUNC_(target, mask) - Generate a tile with the values from the data tile, but where cells in the masking tile contain NODATA, replace the data value with NODATA.",
  arguments = """
  Arguments:
    * target - tile to mask
    * mask - masking definition""",
  examples = """
  Examples:
    > SELECT _FUNC_(target, mask);
       ..."""
)
case class MaskByDefined(targetTile: Expression, maskTile: Expression)
  extends BinaryExpression with MaskExpression
    with CodegenFallback
    with RasterResult {
  override def nodeName: String = "rf_mask"

  def left: Expression = targetTile
  def right: Expression = maskTile

  protected def withNewChildrenInternal(newLeft: Expression, newRight: Expression): Expression =
    MaskByDefined(newLeft, newRight)

  override def checkInputDataTypes(): TypeCheckResult = checkTileDataTypes()

  override protected def nullSafeEval(targetInput: Any, maskInput: Any): Any = {
    val (targetTile, targetCtx) = targetTileExtractor(row(targetInput))
    val (mask, maskCtx) = maskTileExtractor(row(maskInput))
    val result = maskEval(targetTile, mask,
      { (v, m) => if (isNoData(m)) NODATA else v },
      { (v, m) => if (isNoData(m)) NODATA else v }
    )
    toInternalRow(result, targetCtx)
  }
}

object MaskByDefined {
  def apply(targetTile: Column, maskTile: Column): TypedColumn[Any, Tile] =
    new Column(MaskByDefined(targetTile.expr, maskTile.expr)).as[Tile]
}
