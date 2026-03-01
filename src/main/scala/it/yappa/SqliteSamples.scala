package it.yappa

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor

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