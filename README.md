# VectorPipe #

[![Build Status](https://travis-ci.org/geotrellis/vectorpipe.svg?branch=master)](https://travis-ci.org/geotrellis/vectorpipe)
[![Bintray](https://img.shields.io/bintray/v/azavea/maven/vectorpipe.svg)](https://bintray.com/azavea/maven/vectorpipe)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/447170921bc94b3fb494bb2b965c2235)](https://www.codacy.com/app/fosskers/vectorpipe?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=geotrellis/vectorpipe&amp;utm_campaign=Badge_Grade)

VectorPipe (VP) is a library for working with OpenStreetMap (OSM) vector
data. Powered by [Geotrellis](http://geotrellis.io) and [Apache
Spark](http://spark.apache.org/).

OSM provides a wealth of data which has broad coverage and a deep history.
This comes at the price of very large size which can make accessing the power
of OSM difficult.  VectorPipe can help by making OSM processing in Apache
Spark possible, leveraging large computing clusters to churn through the large
volume of, say, an OSM full history file.

For those cases where an application needs to process incoming changes, VP
also provides streaming Spark `DataSource`s for changesets, OsmChange files,
and Augmented diffs generated by Overpass.

For ease of use, the output of VP imports is a Spark DataFrame containing
columns of JTS `Geometry` objects, enabled by the user-defined types provided
by [GeoMesa](https://github.com/locationtech/geomesa).  That package also
provides functions for manipulating those geometries via Spark SQL directives.

The final important contribution is a set of functions for exporting
geometries to vector tiles.  This leans on the `geotrellis-vectortile`
package.

## Getting Started ##

The fastest way to get started with VectorPipe is to invoke `spark-shell` and
load the package jars from the Bintray repository:
```bash
spark-shell --packages com.azavea:vectorpipe_2.11:1.0.0 --repositories http://dl.bintray.com/azavea/maven
```

This will download the required components and set up a REPL with VectorPipe
available.  At which point, you may issue
```scala
// Make JTS types available to Spark
import org.locationtech.geomesa.spark.jts._
spark.withJTS

import vectorpipe._
```
and begin using the package.

#### A Note on Cluster Computing ####

Your local machine is probably insufficient for dealing with very large OSM
files.  We recommend the use of Amazon's Elastic Map Reduce (EMR) service to
provision substantial clusters of computing resources.  You'll want to supply
Spark, Hive, and Hadoop to your cluster, with Spark version 2.3.  Creating a
cluster with EMR version between 5.13 and 5.19 should suffice.  From there,
`ssh` into the master node and run `spark-shell` as above for an interactive
environment, or use `spark-submit` for batch jobs.  (You may submit Steps to
the EMR cluster using `spark-submit` as well.)

### Importing Data ###

Batch analysis can be performed in a few different ways.  Perhaps the fastest
way is to procure an OSM PBF file from a source such as
[GeoFabrik](https://download.geofabrik.de/index.html), which supplies various
extracts of OSM, including the full planet worth of data.

VectorPipe does not provide the means to directly read these OSM PBF files,
however, and a conversion to a useful file format will thus be needed.  We
suggest using [`osm2orc`](https://github.com/mojodna/osm2orc) to convert your
source file to the ORC format which can be read natively via Spark:
```scala
val df = spark.read.orc(path)
```
The resulting `DataFrame` can be processed with VectorPipe.

It is also possible to read from a cache of
[OsmChange](https://wiki.openstreetmap.org/wiki/OsmChange) files directly
rather than convert the PBF file:
```scala
import vectorpipe.sources.Source
val df = spark.read
              .format(Source.Changes)
              .options(Map[String, String](
                Source.BaseURI -> "https://download.geofabrik.de/europe/isle-of-man-updates/",
                Source.StartSequence -> "2080",
                Source.EndSequence -> "2174",
                Source.BatchSize -> "1"))
              .load
              .persist // recommended to avoid rereading
```
(Note that the start and end sequence will shift over time for Geofabrik.
Please navigate to the base URI to determine these values, otherwise timeouts
may occur.)  This may issue errors, but should complete.  This is much slower
than using ORC files and is much touchier, but it stands as an option.

[It is also possible to build a dataframe from a stream of changesets in a
similar manner as above.  Changesets carry additional metadata regarding the
author of the changes, but none of the geometric information.  These tables
can be joined on `changeset`.]

In either case, a useful place to start is to convert the incoming dataframe
into a more usable format.  We recommend calling
```scala
val geoms = OSM.toGeometry(df)
```
which will produce a frame consisting of "top-level" entities, which is to say
nodes that don't participate in a way, ways that don't participate in
relations, and a subset of the relations from the OSM data.  The resulting
dataframe will represent these entities with JTS geometries in the `geom`
column.

It is also possible to filter the results based on information in the tags.
For instance, all buildings can be found as
```scala
import vectorpipe.functions.osm._
val buildings = geoms.filter(isBuilding('tags))
```

Again, the JTS user defined types allow for easier manipulation of and
calculation from geometric types.  See
[here](https://www.geomesa.org/documentation/user/spark/sparksql_functions.html)
for a list of functions that operate on geometries.

## Local Development ##

If you are intending to contribute to VectorPipe, you may need to work with a
development version.  If that is the case, instead of loading from Bintray,
you will need to build a fat jar using
```bash
./sbt assembly
```
and following that,
```bash
spark-shell --jars target/scala_2.11/vectorpipe.jar
```

### IntelliJ IDEA

When developing with IntelliJ IDEA, the sbt plugin will see Spark dependencies
as provided, which will prevent them from being indexed properly, resulting in
errors / warnings within the IDE. To fix this, create `idea.sbt` at the root of
the project:

```scala
import Dependencies._

lazy val mainRunner = project.in(file("mainRunner")).dependsOn(RootProject(file("."))).settings(
  libraryDependencies ++= Seq(
    sparkSql % Compile
  )
)
```
