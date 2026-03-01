ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, GraalVMNativeImagePlugin)
  .settings(
    name := "planning-poker-app",

    Compile / mainClass := Some("it.yappa.Main"),
    GraalVMNativeImage / mainClass := Some("it.yappa.Main"),

    libraryDependencies ++= Seq(
      // Test
      "org.scalameta" %% "munit" % "1.2.2" % Test,

      // Cats
      "org.typelevel" %% "cats-core" % "2.13.0",
      "org.typelevel" %% "cats-effect" % "3.6.3",

      // http4s
      "org.http4s" %% "http4s-ember-server" % "0.23.33",
      "org.http4s" %% "http4s-dsl" % "0.23.33",
      "org.http4s" %% "http4s-core" % "0.23.33",
      "org.http4s" %% "http4s-circe" % "0.23.33",

      // Circe
      "io.circe" %% "circe-generic" % "0.14.15",

      // DB
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC11",
      "org.xerial" % "sqlite-jdbc" % "3.51.1.0",

      // OpenTelemetry
      "org.typelevel" %% "otel4s-oteljava" % "0.15.1",
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.59.0" % Runtime,
      "io.opentelemetry" % "opentelemetry-exporter-prometheus" % "1.59.0-alpha",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.59.0",
    ),

    GraalVMNativeImage / graalVMNativeImageOptions ++= Seq(
      "--no-fallback",
      "--enable-http",
      "--enable-https",
      "--report-unsupported-elements-at-runtime",
      "--enable-native-access=ALL-UNNAMED"
    )
  )