
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .settings(
    name := "yappa"
  )
libraryDependencies +=
  "org.scalameta" %% "munit" % "1.2.2" % Test


assembly / assemblyJarName := "planning-poker-app.jar"