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

package org.locationtech.rasterframes.datasource.rastersource
import org.locationtech.rasterframes.{TestEnvironment, _}
import org.locationtech.rasterframes.datasource.rastersource.RasterSourceDataSource.{RasterSourceCatalog, _}
import org.locationtech.rasterframes.model.TileDimensions
import org.locationtech.rasterframes.util._

class RasterSourceDataSourceSpec extends TestEnvironment with TestData {
  import spark.implicits._

  describe("DataSource parameter processing") {
    def singleCol(paths: Iterable[String]) = {
      val rows: Seq[BandSet] = paths.map(BandSet(_)).toSeq
      RasterSourceCatalog(rows, DEFAULT_COLUMN_NAME)
    }

    it("should handle single `path`") {
      val p = Map(PATH_PARAM -> "/usr/local/foo/bar.tif")
      p.catalog should be (Some(singleCol(p.values)))
    }

    it("should handle single `paths`") {
      val p = Map(PATHS_PARAM -> "/usr/local/foo/bar.tif")
      p.catalog should be (Some(singleCol(p.values)))
    }
    it("should handle multiple `paths`") {
      val expected = Seq("/usr/local/foo/bar.tif", "/usr/local/bar/foo.tif")
      val p = Map(PATHS_PARAM -> expected.mkString("\n\r", "\n\n", "\r"))
      p.catalog should be (Some(singleCol(expected)))
    }
    it("should handle both `path` and `paths`") {
      val expected1 = Seq("/usr/local/foo/bar.tif", "/usr/local/bar/foo.tif")
      val expected2 = "/usr/local/barf/baz.tif"
      val p = Map(PATHS_PARAM -> expected1.mkString("\n"), PATH_PARAM -> expected2)
      p.catalog should be (Some(singleCol(expected1 :+ expected2)))
    }
    it("should parse tile dimensions") {
      val p = Map(TILE_DIMS_PARAM -> "4, 5")
      p.tileDims should be (Some(TileDimensions(4, 5)))
    }

    it("should parse path table specification") {
      val p = Map(CATALOG_TABLE_PARAM -> "catalogTable", CATALOG_TABLE_COLS_PARAM -> "path")
      p.pathSpec should be (Right(RasterSourceCatalogRef("catalogTable", "path")))
    }

    it("should parse path table from CSV") {
      val bands = Seq("B1", "B2", "B3")
      val paths = Seq("/usr/local/foo/bar.tif", "/usr/local/bar/foo.tif", "/usr/local/barf/baz.tif")
      val csv =
        s"""
          |${bands.mkString(",")}
          |${paths.mkString(",")}
        """.stripMargin.trim
      val p = Map(CATALOG_CSV_PARAM -> csv)
      p.pathSpec should be (Left(RasterSourceCatalog(Seq(BandSet(paths:_*)), bands:_*)))
    }
  }

  describe("RasterSource as relation reading") {
    val b = DEFAULT_COLUMN_NAME

    it("should default to a single band schema") {
      val df = spark.read.rastersource.load(l8B1SamplePath.toASCIIString)
      val tcols = df.tileColumns
      tcols.length should be(1)
      tcols.map(_.columnName) should contain(DEFAULT_COLUMN_NAME)
    }
    it("should support a multiband schema") {
      val df = spark.read
        .rastersource
        .withBandIndexes(0, 1, 2)
        .load(cogPath.toASCIIString)
      val tcols = df.tileColumns
      tcols.length should be(3)
      tcols.map(_.columnName) should contain allElementsOf Seq("_b0", "_b1", "_b2").map(s => DEFAULT_COLUMN_NAME + s)
    }
    it("should read a multiband file") {
      val df = spark.read
        .rastersource
        .withBandIndexes(0, 1, 2)
        .load(cogPath.toASCIIString)
        .cache()
      df.schema.size should be (4)
      // Test (roughly) we have three distinct but compabible bands
      val stats = df.agg(rf_agg_stats($"${b}_b0") as "s0", rf_agg_stats($"${b}_b1") as "s1", rf_agg_stats($"${b}_b2") as "s2")
      stats.select($"s0.data_cells" === $"s1.data_cells").as[Boolean].first() should be(true)
      stats.select($"s0.data_cells" === $"s2.data_cells").as[Boolean].first() should be(true)
      stats.select($"s0.mean" =!= $"s1.mean").as[Boolean].first() should be(true)
      stats.select($"s0.mean" =!= $"s2.mean").as[Boolean].first() should be(true)
    }
    it("should read a single file") {
      // Image is 1028 x 989 -> 9 x 8 tiles
      val df = spark.read.rastersource
        .withTileDimensions(128, 128)
        .load(cogPath.toASCIIString)

      df.count() should be(math.ceil(1028.0 / 128).toInt * math.ceil(989.0 / 128).toInt)

      val dims = df.select(rf_dimensions($"$b").as[TileDimensions]).distinct().collect()
      dims should contain allElementsOf
        Seq(TileDimensions(4,128), TileDimensions(128,128), TileDimensions(128,93), TileDimensions(4,93))

      df.select($"${b}_path").distinct().count() should be(1)
    }
    it("should read a multiple files with one band") {
      val df = spark.read.rastersource
        .from(Seq(cogPath, l8B1SamplePath, nonCogPath))
        .withTileDimensions(128, 128)
        .load()
      df.select($"${b}_path").distinct().count() should be(3)
      df.schema.size should be(2)
    }
    it("should read a multiple files with heterogeneous bands") {
      val df = spark.read.rastersource
        .from(Seq(cogPath, l8B1SamplePath, nonCogPath))
        .withTileDimensions(128, 128)
        .withBandIndexes(0, 1, 2, 3)
        .load()
        .cache()
      df.select($"${b}_path").distinct().count() should be(3)
      df.schema.size should be(5)

      df.select($"${b}_b0").count() should be (df.select($"${b}_b0").na.drop.count())
      df.select($"${b}_b1").na.drop.count() shouldBe <(df.count())
      df.select($"${b}_b1").na.drop.count() should be (df.select($"${b}_b2").na.drop.count())
      df.select($"${b}_b3").na.drop.count() should be (0)
    }

    it("should read a set of coherent bands from multiple files from a CSV") {
      val bands = Seq("B1", "B2", "B3")
      val paths = Seq(
        l8SamplePath(1).toASCIIString,
        l8SamplePath(2).toASCIIString,
        l8SamplePath(3).toASCIIString
      )

      val csv =
        s"""
           |${bands.mkString(",")}
           |${paths.mkString(",")}
        """.stripMargin.trim

      val df = spark.read.rastersource
        .fromCSV(csv)
        .withTileDimensions(128, 128)
        .load()

      df.schema.size should be(6)
      df.tileColumns.size should be (3)
      df.select($"B1_path").distinct().count() should be (1)
    }

    it("should read a set of coherent bands from multiple files in a table") {
      val bandPaths = Seq((
        l8SamplePath(1).toASCIIString,
        l8SamplePath(2).toASCIIString,
        l8SamplePath(3).toASCIIString))
        .toDF("B1", "B2", "B3")

      bandPaths.createOrReplaceTempView("pathsTable")

      val df = spark.read.rastersource
        .fromTable("pathsTable", "B1", "B2", "B3")
        .withTileDimensions(128, 128)
        .load()

      df.schema.size should be(6)
      df.tileColumns.size should be (3)
      df.select($"B1_path").distinct().count() should be (1)

      val diffStats = df.select(rf_tile_stats($"B1") =!= rf_tile_stats($"B2")).as[Boolean].collect()
      diffStats.forall(identity) should be(true)
    }
  }
}
