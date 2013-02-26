import sbt._
import Keys._
import trafficland.opensource.sbt.plugins._

object SBTPluginsBuild extends Build {

  lazy val root = Project(id = "sbt-plugins", base = file("."),
    settings = StandardPluginSet.plugs ++
    Seq(
      isApp := false,
      name := "sbt-plugins",
      organization := "com.trafficland",
      organizationName := "TrafficLand, Inc.",
      sbtPlugin := true,
      version       := "0.6.7".toReleaseFormat,
      scalaVersion := "2.9.2",
      scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "1.8" % "test"
      ),
      publishTo <<= (version) { version: String =>
        val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
        val (name, url) = version.isSnapshot match {
          case true => ("community-sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
          case false => ("community-sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
        }
        Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
      },
      publishMavenStyle := false,
      credentials += Credentials(Path.userHome / ".ivy2" / "tlcredentials" / ".scala-sbt-credentials")
    )
  )
}
