
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .settings(
    name := "yappa"
  )
libraryDependencies ++= Seq(
  // Test
  "org.scalameta" %% "munit" % "1.2.2" % Test,

  // Cats
  "org.typelevel" %% "cats-core" % "2.13.0",
  "org.typelevel" %% "cats-effect" % "3.6.3",

  // http4s (Ember server)
  "org.http4s" %% "http4s-ember-server" % "0.23.33",
  "org.http4s" %% "http4s-dsl" % "0.23.33",
  "org.http4s" %% "http4s-core" % "0.23.33",
  "org.http4s" %% "http4s-circe" % "0.23.33",

  // Circe
  "io.circe" %% "circe-generic" % "0.14.15",

  // Doobie
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC11",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC11",

  // SQLite
  "org.xerial" % "sqlite-jdbc" % "3.51.1.0"
)

assembly / assemblyJarName := "planning-poker-app.jar"