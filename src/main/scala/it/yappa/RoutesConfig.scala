package it.yappa

import cats.effect.IO
import it.yappa.Room.{CreateRoomRequest, SubmitVoteRequest}
import io.circe.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*

import it.yappa.Room.{JoinRoomRequest}

import java.time.Instant

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
    case req@GET -> Root / "static" / fileName =>
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
          case Left(_) => BadRequest("invalid vote")
        }
      }

    case PUT -> Root / "room" / roomId / "reveal" =>
      planningPoker.reveal(roomId).attempt.flatMap {
        case Right(room) => Ok(room.toRoomResponse)
        case Left(_) => BadRequest("cannot reveal")
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

    case req@GET -> Root / "test" =>
      for
        _ <- IO.println(s"[REQ] ${req.method} ${req.uri} ${Instant.now()}")
        all <- selectAll
        res <- Ok(all.headOption.map(_.name).getOrElse("no users"))
      yield res
