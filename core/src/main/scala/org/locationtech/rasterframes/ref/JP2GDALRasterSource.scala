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

package org.locationtech.rasterframes.ref

import java.net.URI

import geotrellis.raster.{GridBounds, MultibandTile, Raster}

/**
  * Temporary fix for https://github.com/locationtech/geotrellis/issues/3184, providing thread locking over
  * wrapped GeoTrellis RasterSource
  */
class JP2GDALRasterSource(source: URI) extends GDALRasterSource(source) {

  override protected def tiffInfo = JP2GDALRasterSource.synchronized {
    SimpleRasterInfo(source.toASCIIString, _ => JP2GDALRasterSource.synchronized(SimpleRasterInfo(gdal)))
  }

  override def readBounds(bounds: Traversable[GridBounds[Int]], bands: Seq[Int]): Iterator[Raster[MultibandTile]] =
    JP2GDALRasterSource.synchronized {
      super.readBounds(bounds, bands)
    }
  override def read(bounds: GridBounds[Int], bands: Seq[Int]): Raster[MultibandTile] =
    JP2GDALRasterSource.synchronized {
      readBounds(Seq(bounds), bands).next()
    }
}

object JP2GDALRasterSource {
  def apply(source: URI): JP2GDALRasterSource = new JP2GDALRasterSource(source)
}


