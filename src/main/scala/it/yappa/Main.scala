package it.yappa

import cats.effect.{Clock, IO, IOApp}
import com.comcast.ip4s.{host, port}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe.*

import io.circe.*
import io.circe.generic.semiauto.*

import it.yappa.Room.CreateRoomRequest
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder

// ===== JSON request decoding =====
given Decoder[CreateRoomRequest] = deriveDecoder
given EntityDecoder[IO, CreateRoomRequest] = jsonOf[IO, CreateRoomRequest]

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

  // --- tiny helper for consistent error handling ---
  private def logAnd500(where: String, e: Throwable): IO[Response[IO]] =
    IO.println(s"[ERROR] $where: ${e.getClass.getName}: ${e.getMessage}") *>
      IO.println(e) *>
      InternalServerError("internal server error")

  private def routes(planningPoker: PlanningPoker[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO]:

      case GET -> Root =>
        Ok("OK: scala3 + cats + http4s\n")

      case GET -> Root / "health" =>
        Ok("healthy")

      case GET -> Root / "room" / id =>
        planningPoker.find(id).attempt.flatMap {
          case Right(InvalidResponse) =>
            NotFound()

          case Right(v: ValidResponse) =>
            Ok(v) // requires Encoder[ValidResponse]

          case Left(e) =>
            logAnd500(s"GET /room/$id failed", e)
        }

      case req @ POST -> Root / "room" =>
        req.as[CreateRoomRequest].attempt.flatMap {
          case Left(e) =>
            // To zwykle będzie błąd dekodowania JSON – semantycznie 400
            IO.println(s"[WARN] POST /room decode failed: ${e.getMessage}") *>
              BadRequest("invalid json body")

          case Right(body) =>
            planningPoker.create(body).attempt.flatMap {
              case Right(created) =>
                Created(created.toRoomResponse) // requires Encoder[CreateResponseType]

              case Left(e) =>
                logAnd500("POST /room create failed", e)
            }
        }

      case req @ GET -> Root / "ui" =>
        StaticFile
          .fromResource("/public/index.html", Some(req))
          .getOrElseF(NotFound())

      case req @ GET -> Root / "game" =>
        StaticFile
          .fromResource("/public/game.html", Some(req))
          .getOrElseF(NotFound())

      case req @ POST -> Root / "echo" =>
        req.as[String].flatMap(body => Ok(body))

  override def run: IO[Unit] =
    for
      start <- Clock[IO].monotonic
      _     <- IO.println(logo)
      _     <- IO.println("Starting HTTP server...")

      planningPoker <- PlanningPoker.create[IO]

      _ <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(routes(planningPoker).orNotFound)
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