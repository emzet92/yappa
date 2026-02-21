package it.yappa

import java.time.Instant
import java.util.UUID

object Main {
  def main(args: Array[String]): Unit = {
    val mateusz = Participant(
      ParticipantId(UUID.randomUUID()),
      "Mateusz",
      true)

    val stringOrRoom = Room.create("some-session")
      .addParticipant(mateusz)
      .startVoting(Instant.now())

    println(stringOrRoom)
  }
}
