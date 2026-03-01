package it.yappa

import cats.Monad
import cats.effect.{IO, Ref, Sync}
import cats.implicits.{toFlatMapOps, toFunctorOps}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import it.yappa.Room.{CreateRoomRequest, JoinRoomRequest, SubmitVoteRequest}
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

import java.time.Instant
import java.util.UUID

class PlanningPoker[F[_] : Monad](repository: RoomRepository[F]) {
  def createRoom(req: CreateRoomRequest): F[Room] = {
    println(s"Create room!! $req")
    repository.save(Room.create(req))
  }

  def startVoting(roomId: String): F[Room] = repository.get(RoomId(UUID.fromString(roomId))).flatMap {
    case Some(value) =>
      repository.save(value.startVoting(Instant.now()).getOrElse(null))
    case None => ???
  }

  def submitVote(roomId: String, req: SubmitVoteRequest): F[Room] =
    repository.get(RoomId(UUID.fromString(roomId))).flatMap {
      case None => ???
      case Some(room) =>
        val submittedVote = room
          .submitVote(ParticipantId(UUID.fromString(req.participantId)), req.value).getOrElse(null)
        repository.save(submittedVote)
    }

  def reveal(roomId: String): F[Room] =
    repository.get(RoomId(UUID.fromString(roomId))).flatMap {
      case Some(room) =>
        repository.save(room.reveal(Instant.now()).getOrElse(throw new RuntimeException("Cannot reveal")))
      case None =>
        Monad[F].pure(throw new RuntimeException("Room not found"))
    }

  def join(roomId: String, name: String): F[Room] =
    repository.get(RoomId(UUID.fromString(roomId))).flatMap {
      case Some(room) =>
        val updated = room.join(name)
        repository.save(updated)

      case None =>
        Monad[F].pure(throw new RuntimeException("Room not found"))
    }

  def find(id: String): F[RoomResponse] = repository
    .get(RoomId(UUID.fromString(id)))
    .map {
      case Some(room) => room.toRoomResponse
      case None => InvalidResponse
    }
}

object PlanningPoker:
  def create[F[_] : Sync]: F[PlanningPoker[F]] =
    InMemoryRoomRepository.create[F]
      .map(repo => PlanningPoker(repo))


given Encoder[ValidResponse] = deriveEncoder
given Encoder[ParticipantResponse] = deriveEncoder
given Encoder[CurrentRoundResponse] = deriveEncoder

trait RoomResponse

case class ValidResponse(id: String,
                         roomName: String,
                         state: String,
                         participants: List[ParticipantResponse],
                         currentRoundResponse: Option[CurrentRoundResponse]) extends RoomResponse

case object InvalidResponse extends RoomResponse

case class ParticipantResponse(id: String, name: String, isAdmin: Boolean)

case class CurrentRoundResponse(id: String, votes: Map[String, String])

extension (round: Round) {
  def toResponse: CurrentRoundResponse = CurrentRoundResponse(
    id = round.id.toString,
    votes = round.votes.map {
      case (k, v) => (k.value.toString -> v)
    }
  )
}

extension (p: Map[ParticipantId, Participant]) {
  def toResponse: List[ParticipantResponse] = p.toList.map(p1 => ParticipantResponse(p1._2.id.value.toString(), p1._2.name, p1._2.isAdmin))
}

extension (room: Room)
  def toRoomResponse: ValidResponse = ValidResponse(
    id = room.id.value.toString,
    roomName = room.roomName,
    state = room.state.toString,
    participants = room.participants.toResponse,
    currentRoundResponse = room.currentRound.map(f => f.toResponse)
  )


trait RoomRepository[F[_]]:
  def get(id: RoomId): F[Option[Room]]

  def save(room: Room): F[Room]

  def all: F[List[Room]]

class InMemoryRoomRepository[F[_] : Sync] private(
                                                   ref: Ref[F, Map[RoomId, Room]]
                                                 ) extends RoomRepository[F]:

  def get(id: RoomId): F[Option[Room]] =
    ref.get.map(_.get(id))

  def save(room: Room): F[Room] =
    ref.update(_.updated(room.id, room))
      .map(_ => room)

  def all: F[List[Room]] =
    ref.get.map(_.values.toList)

object InMemoryRoomRepository:

  def create[F[_] : Sync]: F[InMemoryRoomRepository[F]] =
    Ref.of[F, Map[RoomId, Room]](Map.empty)
      .map(new InMemoryRoomRepository(_))

// ===== JSON =====
given Decoder[CreateRoomRequest] = deriveDecoder
given EntityDecoder[IO, CreateRoomRequest] = jsonOf[IO, CreateRoomRequest]
given Decoder[SubmitVoteRequest] = deriveDecoder
given EntityDecoder[IO, SubmitVoteRequest] = jsonOf[IO, SubmitVoteRequest]
given Decoder[JoinRoomRequest] = deriveDecoder
given EntityDecoder[IO, JoinRoomRequest] = jsonOf[IO, JoinRoomRequest]