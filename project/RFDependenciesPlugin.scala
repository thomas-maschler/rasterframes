
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

import sbt.Keys._
import sbt._

object RFDependenciesPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  object autoImport {
    val rfSparkVersion = settingKey[String]("Apache Spark version")
    val rfGeoTrellisVersion = settingKey[String]("GeoTrellis version")
    val rfGeoMesaVersion = settingKey[String]("GeoMesa version")

    def geotrellis(module: String) = Def.setting {
      "org.locationtech.geotrellis" %% s"geotrellis-$module" % rfGeoTrellisVersion.value
    }
    def spark(module: String) = Def.setting {
      "org.apache.spark" %% s"spark-$module" % rfSparkVersion.value
    }
    def geomesa(module: String) = Def.setting {
      "org.locationtech.geomesa" %% s"geomesa-$module" % rfGeoMesaVersion.value
    }

    val scalatest = "org.scalatest" %% "scalatest" % "3.2.5" % Test
    val shapeless = "com.chuusai" %% "shapeless" % "2.3.3"
    val `jts-core` = "org.locationtech.jts" % "jts-core" % "1.17.0"
    val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.28"
    val scaffeine = "com.github.blemale" %% "scaffeine" % "4.0.2"
    val `spray-json` = "io.spray" %%  "spray-json" % "1.3.4"
    val `scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
    val stac4s = "com.azavea.stac4s" %% "client" % "0.5.0-13-g35ad8d4-SNAPSHOT"
  }
  import autoImport._

  override def projectSettings = Seq(
    resolvers ++= Seq(
      "Azavea Public Builds" at "https://dl.bintray.com/azavea/geotrellis",
      "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
      "boundless-releases" at "https://repo.boundlessgeo.com/main/",
      "Open Source Geospatial Foundation Repository" at "https://download.osgeo.org/webdav/geotools/",
      "oss-snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "jitpack" at "https://jitpack.io"
    ),
    // dependencyOverrides += "com.azavea.gdal" % "gdal-warp-bindings" % "33.f746890",
    // NB: Make sure to update the Spark version in pyrasterframes/python/setup.py
    rfSparkVersion := "3.1.1",
    rfGeoTrellisVersion := "3.6.0",
    rfGeoMesaVersion := "3.2.0"
  )
}
