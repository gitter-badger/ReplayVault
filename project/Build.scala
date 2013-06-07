import sbt._
import Keys._

object MyBuild extends Build {

  lazy val copyDependencies = TaskKey[Unit]("copy-dependencies")

  def copyDepTask = copyDependencies <<= (update, crossTarget, scalaVersion) map {
    (updateReport, out, scalaVer) => {
      val builder = StringBuilder.newBuilder
      updateReport.select(configuration = Set("compile")).filter(!_.getName.endsWith("scala-compiler.jar")) foreach { srcPath =>
        val destPath = out / "lib" / srcPath.getName
        builder ++= "code = lib/%s \n".format(srcPath.getName)
        IO.copyFile(srcPath, destPath, preserveLastModified=true)
      }
      IO.write(out / "lib" / "code.txt",builder.toString.getBytes)
    }

  }

  import Dependencies._
  val deps = Seq(
    dispatch, akka, akkaSLF4J, swing, logula, configrity,
    subcut, slf4j, specs2, mockito, scalaIOcore, scalafile
  )

  lazy val root = Project(
    "root",
    file("."),
    settings = Defaults.defaultSettings ++ Seq(
      copyDepTask,
      scalacOptions ++= Seq("-unchecked","-deprecation"),
      libraryDependencies ++= deps,
      resolvers ++= Resolvers.resolvers,
      javacOptions ++= Seq("-source", "1.6")
    )
  )
}

object Resolvers {
  val resolvers = Seq(
    "coda hale's repo" at "http://repo.codahale.com",
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/")

}


object Dependencies {

  val akkaVersion = "2.0.1"
  val scalaio = "0.4.0"

  val dispatch = "net.databinder" %% "dispatch-http" % "0.8.7"
  val akka = "com.typesafe.akka" % "akka-actor" % akkaVersion
  val akkaSLF4J = "com.typesafe.akka" % "akka-slf4j" % akkaVersion
  val swing = "org.scala-lang" % "scala-swing" % "2.9.1" withSources
  val logula = "com.codahale" %% "logula" % "2.1.3"
  val configrity = "org.streum" %% "configrity-core" % "0.10.2"
  val subcut = "org.scala-tools.subcut" %% "subcut" % "1.0"
  val slf4j = "org.slf4j" % "slf4j-log4j12" % "1.6.4"
  val specs2 = "org.specs2" %% "specs2" % "1.9" % "test"
  val mockito = "org.mockito" % "mockito-core" % "1.9.0" % "test"
  val scalaIOcore = "com.github.scala-incubator.io" %% "scala-io-core" % scalaio
  val scalafile = "com.github.scala-incubator.io" %% "scala-io-file" % scalaio

}
