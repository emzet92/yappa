package it.yappa

import cats.effect.{Clock, IO, IOApp}
import com.comcast.ip4s.{host, port}
import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import io.opentelemetry.exporter.prometheus.PrometheusMetricReader
import org.typelevel.otel4s.oteljava.OtelJava

import scala.concurrent.duration.*


case class User(id: Long, name: String)

// ===== SQLite Transactor (NO Hikari) =====
val xa: Transactor[IO] =
  Transactor.fromDriverManager[IO](
    driver = "org.sqlite.JDBC",
    url = "jdbc:sqlite:app.db",
    logHandler = None
  )

// ===== DB INIT =====
def initDb: IO[Unit] =
  sql"""
    CREATE TABLE IF NOT EXISTS users (
      id   INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL
    )
  """.update.run.transact(xa).void

def insertUser(name: String): IO[Unit] =
  sql"""
    INSERT INTO users (name)
    VALUES ($name)
  """.update.run.transact(xa).void

def selectAll: IO[List[User]] =
  sql"""
    SELECT id, name
    FROM users
  """.query[User].to[List].transact(xa)

object Main extends IOApp.Simple:

  val logo =
    """
      |  _   _  __ _ _ __  _ __   __ _
      | | | | |/ _` | '_ \| '_ \ / _` |
      | | |_| | (_| | |_) | |_) | (_| |
      |  \__, |\__,_| .__/| .__/ \__,_|
      |   __/ |     | |   | |
      |  |___/      |_|   |_|
      """.stripMargin


  override def run: IO[Unit] =
    OtelJava.autoConfigured[IO] { builder =>
      builder
        .addPropertiesCustomizer(_ =>
          java.util.Map.of(
            "otel.metrics.exporter", "prometheus",
            "otel.exporter.prometheus.port", "9464"
          )
        )
        .addMeterProviderCustomizer((b, _) =>
          b.registerMetricReader(PrometheusMetricReader.create())
        )
    }.use { otel =>
      for
        start <- Clock[IO].monotonic
        _ <- IO.println(logo)
        _ <- IO.println(s"PID: ${ProcessHandle.current().pid()}")
        _ <- IO.println("Starting HTTP server...")

        // ===== METRICS =====
        meter <- otel.meterProvider.meter("it.yappa").get
        metrics <- AppMetrics.create(meter)

        // ===== INIT DB =====
        _ <- initDb
        _ <- insertUser("Mateusz")
        users <- selectAll
        _ <- IO.println(s"Users in DB: $users")

        planningPoker <- PlanningPoker.create[IO]

        app = AppMetrics.middleware(metrics)(routes(planningPoker, metrics).orNotFound)

        _ <- EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(app)
          .build
          .evalTap { _ =>
            for
              end <- Clock[IO].monotonic
              took = (end - start).toMillis
              _ <- IO.println(s"HTTP server started in ${took} ms 🚀")
            yield ()
          }
          .useForever
      yield ()
    }