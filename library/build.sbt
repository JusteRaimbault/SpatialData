scalaVersion := "2.13.1"

name := "spatialdata"

organization := "org.openmole.library"

resolvers ++= Seq(
  "apache" at "https://repo.maven.apache.org/maven2",
  "osgeo" at "https://repo.osgeo.org/repository/geotools-releases", // for geotools
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("staging"),
  Resolver.mavenCentral
)

val geotoolsVersion = "23.0"

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "com.github.pathikrit" %% "better-files" % "3.8.0",
  "org.locationtech.jts" % "jts" % "1.16.1" pomOnly(),
  "org.geotools" % "gt-shapefile" % geotoolsVersion exclude("javax.media", "jai_core") exclude("com.vividsolutions", "jts-core"),
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  "org.postgresql" % "postgresql" % "42.2.5",
  "org.mongodb" % "mongo-java-driver" % "3.10.0",
  "org.jgrapht" % "jgrapht-core" % "1.3.1",
  "org.apache.httpcomponents" % "httpclient" % "4.3.5",
  "commons-io" % "commons-io" % "2.3",
  "org.apache.commons" % "commons-lang3" % "3.1",
  "it.uniroma1.dis.wsngroup.gexf4j" % "gexf4j" % "1.0.0",
  "org.scalanlp" %% "breeze" % "1.0",
  "com.github.fommil.netlib" % "all" % "1.1.2", // impl for breeze
  "de.ruedigermoeller" % "fst" % "2.57"
)



//lazy val osmrealmeasures = Project("osmrealmeasures", file("target/osmrealmeasures")) settings(
//  mainClass in (Compile, packageBin) := Some("org.openmole.spatialdata.application.urbmorph.OSMRealMeasures")
//)

//lazy val runtest = Project("runtest", file("target/test")) settings(
//  mainClass in (Compile, packageBin) := Some("org.openmole.spatialdata.test.Test"),

//mainClass in (Compile,run) := Some("org.openmole.spatialdata.test.Test")
//fork in run := true

cancelable in Global := true

//)

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation","-feature")

enablePlugins(SbtOsgi)

//lazy val omlplugin = Project("omlplugin", file("target/omlplugin")) enablePlugins SbtOsgi settings(
//  name := "spatialdata",
 //org.openmole.spatialdata.application.*
OsgiKeys.exportPackage := Seq("*;-split-package:=merge-first")//,
  // export only application ? NO for inclusion in OpenMOLE need more - BUT done in OML !
  //OsgiKeys.exportPackage := Seq("org.openmole.spatialdata.application")
OsgiKeys.importPackage := Seq("*;resolution:=optional;-split-package:=merge-first")//,
  //OsgiKeys.privatePackage := Seq("org.openmole.spatialdata.grid,org.openmole.spatialdata.network,org.openmole.spatialdata.run,org.openmole.spatialdata.test,org.openmole.spatialdata.utils,org.openmole.spatialdata.vector,!scala.*,!java.*,!monocle.*,!META-INF.*.RSA,!META-INF.*.SF,!META-INF.*.DSA,META-INF.services.*,META-INF.*,*")//,
OsgiKeys.privatePackage := Seq("!scala.*,!java.*,!monocle.*,!META-INF.*.RSA,!META-INF.*.SF,!META-INF.*.DSA,META-INF.services.*,META-INF.*,*")//,
OsgiKeys.requireCapability := """osgi.ee;filter:="(&(osgi.ee=JavaSE)(version=1.8))""""
//)


/*
// publish fat jar
// https://github.com/sbt/sbt-assembly#publishing-not-recommended
lazy val assemble = Project("assemble", file("target/assemble")) settings (
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.last
  },
  assemblyJarName in assembly := name+"_"+scalaVersion+".jar",
  artifact in (Compile, assembly) := {
    val art = (artifact in (Compile, assembly)).value
    art.withClassifier(Some("assembly"))
  },
  addArtifact(artifact in (Compile, assembly), assembly)
)
*/


/**
  * Testing with scalatest
  */

libraryDependencies += "org.scalactic" %% "scalactic" % "3.1.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0" //% "test"



/**
  * Publishing
  */

useGpg := true

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

// use to overwrite when publish non-snapshot if issue during a previous release tentative
publishConfiguration := publishConfiguration.value.withOverwrite(true)

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

licenses in ThisBuild := Seq("Affero GPLv3" -> url("http://www.gnu.org/licenses/"))

homepage in ThisBuild := Some(url("https://github.com/openmole/spatialdata"))

scmInfo in ThisBuild := Some(ScmInfo(url("https://github.com/openmole/spatialdata.git"), "scm:git:git@github.com:openmole/spatialdata.git"))

pomExtra in ThisBuild :=
  <developers>
    <developer>
      <id>justeraimbault</id>
      <name>Juste Raimbault</name>
    </developer>
    <developer>
      <id>julienperret</id>
      <name>Julien Perret</name>
    </developer>
  </developers>


/**
  * Releasing
  */


sonatypeProfileName := "org.openmole"

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  setReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  //setNextVersion,
  //commitNextVersion,
  releaseStepCommand("sonatypeRelease")
  //releaseStepCommand("sonatypeReleaseAll")//,
  //pushChanges
)

