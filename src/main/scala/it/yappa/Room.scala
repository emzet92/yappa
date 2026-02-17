package it.yappa

import java.time.Instant
import java.util.UUID


enum RoomState:
  case Waiting
  case Voting
  case Revealed

case class ParticipantId(value: UUID)
case class RoomId(value: UUID)

case class Participant(
                        id: ParticipantId,
                        name: String,
                        isAdmin: Boolean,
                        isConnected: Boolean = true
                      )

case class Round(
                  id: UUID,
                  votes: Map[ParticipantId, String],
                  startedAt: Instant,
                  revealedAt: Option[Instant] = None
                ):

  def submitVote(participantId: ParticipantId, value: String): Round =
    copy(votes = votes.updated(participantId, value))

  def reveal(now: Instant): Round =
    copy(revealedAt = Some(now))

  def hasVoted(participantId: ParticipantId): Boolean =
    votes.contains(participantId)

  def results: Map[String, Int] =
    votes.values.groupBy(identity).view.mapValues(_.size).toMap


case class Room(
                 id: RoomId,
                 adminSessionId: String,
                 state: RoomState,
                 participants: Map[ParticipantId, Participant],
                 currentRound: Option[Round]
               ):

  // =====================
  // Participant management
  // =====================

  def addParticipant(participant: Participant): Room =
    copy(participants = participants.updated(participant.id, participant))

  def removeParticipant(participantId: ParticipantId): Room =
    copy(participants = participants - participantId)

  // =====================
  // Voting lifecycle
  // =====================

  def startVoting(now: Instant): Either[String, Room] =
    state match
      case RoomState.Waiting | RoomState.Revealed =>
        val newRound = Round(
          id = UUID.randomUUID(),
          votes = Map.empty,
          startedAt = now
        )
        Right(copy(
          state = RoomState.Voting,
          currentRound = Some(newRound)
        ))

      case RoomState.Voting =>
        Left("Voting already in progress")

  def submitVote(
                  participantId: ParticipantId,
                  value: String
                ): Either[String, Room] =
    (state, currentRound) match
      case (RoomState.Voting, Some(round)) =>
        if !participants.contains(participantId) then
          Left("Participant not in room")
        else
          val updatedRound = round.submitVote(participantId, value)
          Right(copy(currentRound = Some(updatedRound)))

      case _ =>
        Left("Voting is not active")

  def reveal(now: Instant): Either[String, Room] =
    (state, currentRound) match
      case (RoomState.Voting, Some(round)) =>
        val revealedRound = round.reveal(now)
        Right(copy(
          state = RoomState.Revealed,
          currentRound = Some(revealedRound)
        ))

      case _ =>
        Left("Cannot reveal votes now")

  def reset: Room =
    copy(
      state = RoomState.Waiting,
      currentRound = None
    )
  

  def allParticipantsVoted: Boolean =
    currentRound match
      case Some(round) =>
        participants.keySet.subsetOf(round.votes.keySet)
      case None =>
        false

object Room:
  def create(adminSessionId: String): Room =
    Room(
      id = RoomId(UUID.randomUUID()),
      adminSessionId = adminSessionId,
      state = RoomState.Waiting,
      participants = Map.empty,
      currentRound = None
    )

