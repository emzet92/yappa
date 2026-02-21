package it.yappa

import cats.effect.{Clock, IO, IOApp}
import com.comcast.ip4s.{host, port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.*
import org.http4s.dsl.io.*

import java.time.Instant
import java.util.UUID
import io.circe.*
import io.circe.generic.semiauto.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder

given Encoder[RoomResponse] = deriveEncoder

case class RoomResponse(id: String)

extension (room: Room)
  def toRoomResponse = RoomResponse(room.id.value.toString)

object Main extends IOApp.Simple:

  val mateusz = Participant(
    ParticipantId(UUID.randomUUID()),
    "Mateusz",
    true)

  val stringOrRoom = Room.create("some-session")
    .addParticipant(mateusz)
    .startVoting(Instant.now()).getOrElse(null)

  val romRepository = Map("1" -> stringOrRoom)

  val logo =
    """
      |  _   _  __ _ _ __  _ __   __ _
      | | | | |/ _` | '_ \| '_ \ / _` |
      | | |_| | (_| | |_) | |_) | (_| |
      |  \__, |\__,_| .__/| .__/ \__,_|
      |   __/ |     | |   | |
      |  |___/      |_|   |_|
          """.stripMargin

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO]:
    case GET -> Root =>
      Ok("OK: scala3 + cats + http4s\n")

    case GET -> Root / "health" =>
      Ok("healthy")

    case GET -> Root / "room" / id =>
    romRepository.get(id) match
      case Some(room) => Ok(room.toRoomResponse)
      case None       => NotFound()


    case req@POST -> Root / "echo" =>
      req.as[String].flatMap(body => Ok(body))

  private val app: HttpApp[IO] =
    routes.orNotFound

  override def run: IO[Unit] =
    for
      start <- Clock[IO].monotonic
      _ <- IO.println(logo)
      _ <- IO.println("Starting HTTP server...")

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
            _ <- IO.println(
              s"HTTP server started in ${took} ms 🚀"
            )
          yield ()
        }
        .useForever
    yield ()

