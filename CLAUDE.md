# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Yappa is a Planning Poker web application built with Scala 3 using a functional, effect-based architecture. It allows teams to create rooms, join sessions, vote on estimates, and reveal results.

## Commands

```bash
# Compile
sbt compile

# Run the application (starts HTTP server on port 8080)
sbt run

# Run all tests
sbt test

# Build a GraalVM native image
sbt graalVMNativeImage
```

## Architecture

**Stack:** Scala 3.3.1, Cats Effect 3, http4s (Ember), Circe, Doobie + SQLite

**Source layout:**
- `src/main/scala/it/yappa/Room.scala` — Domain models (`Room`, `Round`, `Participant`, `RoomState`, value objects) with pure functions for all state transitions
- `src/main/scala/it/yappa/PlanningPoker.scala` — Service layer (`PlanningPoker[F]`) and repository abstraction (`RoomRepository[F]`) with an in-memory `Ref`-backed implementation
- `src/main/scala/it/yappa/Main.scala` — http4s routes, SQLite initialization via Doobie, Ember server setup on port 8080
- `src/main/resources/public/` — Static frontend (index.html for room creation, game.html for voting)

**Key patterns:**
- Tagless final: `PlanningPoker[F[_]]` and `RoomRepository[F[_]]` are parameterized over any Cats Effect-compatible effect
- Domain is pure/immutable; all mutations go through `Ref[F, Map[RoomId, Room]]` in the in-memory repository
- SQLite (`app.db`) is initialized on startup with a `users` table but room state is currently held in-memory

**API routes:**
```
POST /room            — Create room
POST /room/{id}/join  — Join room
GET  /room/{id}       — Get room state
PUT  /room/{id}       — Start voting round
PUT  /room/{id}/vote  — Submit vote
GET  /ui              — Room creation UI
GET  /game            — Game/voting UI
```

**GraalVM native image** is supported; reflection config lives in `src/main/resources/META-INF/native-image/reflect-config.json`.
