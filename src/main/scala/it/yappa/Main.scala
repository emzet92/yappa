package it.yappa

import cats.effect.{Clock, IO, IOApp}
import com.comcast.ip4s.{host, port}
import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import io.circe.*
import io.circe.generic.semiauto.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import io.opentelemetry.exporter.prometheus.PrometheusMetricReader
import io.prometheus.metrics.expositionformats.ExpositionFormats
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.typelevel.otel4s.oteljava.OtelJava

import java.io.ByteArrayOutputStream
import scala.concurrent.duration.*
import it.yappa.Room.{CreateRoomRequest, JoinRoomRequest, SubmitVoteRequest}

import java.time.Instant

// ===== JSON =====
given Decoder[CreateRoomRequest] = deriveDecoder
given EntityDecoder[IO, CreateRoomRequest] = jsonOf[IO, CreateRoomRequest]
given Decoder[SubmitVoteRequest] = deriveDecoder
given EntityDecoder[IO, SubmitVoteRequest] = jsonOf[IO, SubmitVoteRequest]
given Decoder[JoinRoomRequest] = deriveDecoder
given EntityDecoder[IO, JoinRoomRequest] = jsonOf[IO, JoinRoomRequest]

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

  private def logAnd500(where: String, e: Throwable): IO[Response[IO]] =
    IO.println(s"[ERROR] $where: ${e.getMessage}") *>
      InternalServerError("internal server error")

  private def routes(planningPoker: PlanningPoker[IO], metrics: AppMetrics): HttpRoutes[IO] =
    HttpRoutes.of[IO]:

      case GET -> Root =>
        Ok("OK: scala3 + cats + http4s\n")

      case GET -> Root / "health" =>
        Ok("healthy")

      case GET -> Root / "room" / id =>
        planningPoker.find(id).attempt.flatMap {
          case Right(InvalidResponse) => NotFound()
          case Right(v: ValidResponse) => Ok(v)
          case Left(e) => logAnd500(s"GET /room/$id", e)
        }

      case req@POST -> Root / "room" =>
        req.as[CreateRoomRequest].attempt.flatMap {
          case Left(_) =>
            BadRequest("invalid json body")

          case Right(body) =>
            planningPoker.createRoom(body).attempt.flatMap {
              case Right(created) =>
                metrics.roomsCreated.add(1L) *> Created(created.toRoomResponse)
              case Left(e) =>
                logAnd500("POST /room", e)
            }
        }
      case req @ GET -> Root / "static" / fileName =>
        StaticFile
          .fromResource(s"/public/static/$fileName", Some(req))
          .getOrElseF(NotFound())

      case req@GET -> Root / "ui" =>
        StaticFile
          .fromResource("/public/index.html", Some(req))
          .getOrElseF(NotFound())

      case req@GET -> Root / "game" =>
        StaticFile
          .fromResource("/public/game.html", Some(req))
          .getOrElseF(NotFound())

      case PUT -> Root / "room" / id =>
        planningPoker.startVoting(id)
          .flatMap(room => Ok(room.toRoomResponse))
          .handleErrorWith(_ => BadRequest("cannot start voting"))

      case req@PUT -> Root / "room" / roomId / "vote" =>
        req.as[SubmitVoteRequest].flatMap { body =>
          planningPoker.submitVote(roomId, body).attempt.flatMap {
            case Right(room) => metrics.votesSubmitted.add(1L) *> Ok(room.toRoomResponse)
            case Left(_)     => BadRequest("invalid vote")
          }
        }

      case PUT -> Root / "room" / roomId / "reveal" =>
        planningPoker.reveal(roomId).attempt.flatMap {
          case Right(room) => Ok(room.toRoomResponse)
          case Left(_)     => BadRequest("cannot reveal")
        }

      case req@POST -> Root / "room" / roomId / "join" =>
        req.as[JoinRoomRequest].attempt.flatMap {
          case Left(_) =>
            BadRequest("invalid json body")

          case Right(body) =>
            planningPoker.join(roomId, body.name).attempt.flatMap {
              case Right(room) =>
                metrics.participantsJoined.add(1L) *> Ok(room.toRoomResponse)

              case Left(_) =>
                NotFound("room not found")
            }
        }

      case GET -> Root / "metrics" =>
        IO.blocking {
          val snapshots = PrometheusRegistry.defaultRegistry.scrape()
          val baos = new ByteArrayOutputStream()
          ExpositionFormats.init().getPrometheusTextFormatWriter().write(baos, snapshots)
          baos.toString("UTF-8")
        }.flatMap(Ok(_))

      case req@GET -> Root / "test" =>
        for
          _ <- IO.println(s"[REQ] ${req.method} ${req.uri} ${Instant.now()}")
          all <- selectAll
          res <- Ok(all.headOption.map(_.name).getOrElse("no users"))
        yield res

  override def run: IO[Unit] =
    OtelJava.autoConfigured[IO] { builder =>
      builder
        .addPropertiesCustomizer(_ => java.util.Map.of("otel.metrics.exporter", "none"))
        .addMeterProviderCustomizer((b, _) =>
          b.registerMetricReader(PrometheusMetricReader.create())
        )
    }.use { otel =>
      for
        start   <- Clock[IO].monotonic
        _       <- IO.println(logo)
        _       <- IO.println(s"PID: ${ProcessHandle.current().pid()}")
        _       <- IO.println("Starting HTTP server...")

        // ===== METRICS =====
        meter   <- otel.meterProvider.meter("it.yappa").get
        metrics <- AppMetrics.create(meter)

        // ===== INIT DB =====
        _     <- initDb
        _     <- insertUser("Mateusz")
        users <- selectAll
        _     <- IO.println(s"Users in DB: $users")

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
              end  <- Clock[IO].monotonic
              took  = (end - start).toMillis
              _    <- IO.println(s"HTTP server started in ${took} ms 🚀")
            yield ()
          }
          .useForever
      yield ()
    }