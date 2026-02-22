package it.yappa

import cats.Monad
import cats.effect.{Ref, Sync}
import cats.implicits.toFunctorOps
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import it.yappa.Room.CreateRoomRequest

import java.util.UUID

class PlanningPoker[F[_] : Monad](repository: RoomRepository[F]) {
  def createRoom(req: CreateRoomRequest): F[Room] = repository.save(Room.create(req))

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
                         participants: List[ParticipantResponse],
                         currentRoundResponse: Option[CurrentRoundResponse]) extends RoomResponse

case object InvalidResponse extends RoomResponse

case class ParticipantResponse(id: String, name: String)

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
  def toResponse: List[ParticipantResponse] = p.toList.map(p1 => ParticipantResponse(p1._2.id.value.toString(), p1._2.name))
}

extension (room: Room)
  def toRoomResponse: ValidResponse = ValidResponse(
    id = room.id.value.toString,
    roomName = room.roomName,
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