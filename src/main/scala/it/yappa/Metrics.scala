package it.yappa

import cats.data.Kleisli
import cats.effect.IO
import org.http4s.HttpApp
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.{Counter, Histogram, Meter}

case class AppMetrics(
  roomsCreated: Counter[IO, Long],
  votesSubmitted: Counter[IO, Long],
  participantsJoined: Counter[IO, Long],
  httpRequestsTotal: Counter[IO, Long],
  httpRequestDuration: Histogram[IO, Double]
)

object AppMetrics:

  def create(meter: Meter[IO]): IO[AppMetrics] =
    for
      roomsCreated <- meter
        .counter[Long]("yappa.rooms.created")
        .withDescription("Total planning poker rooms created")
        .create
      votesSubmitted <- meter
        .counter[Long]("yappa.votes.submitted")
        .withDescription("Total votes submitted across all rooms")
        .create
      participantsJoined <- meter
        .counter[Long]("yappa.participants.joined")
        .withDescription("Total participants who joined a room")
        .create
      httpRequestsTotal <- meter
        .counter[Long]("http.server.requests.total")
        .withDescription("Total HTTP requests by method and status code")
        .create
      httpRequestDuration <- meter
        .histogram[Double]("http.server.request.duration")
        .withDescription("HTTP request duration in milliseconds")
        .withUnit("ms")
        .create
    yield AppMetrics(
      roomsCreated,
      votesSubmitted,
      participantsJoined,
      httpRequestsTotal,
      httpRequestDuration
    )

  def middleware(metrics: AppMetrics)(app: HttpApp[IO]): HttpApp[IO] =
    Kleisli { req =>
      for
        start <- IO.monotonic
        resp  <- app(req)
        end   <- IO.monotonic
        ms     = (end - start).toMillis.toDouble
        _     <- metrics.httpRequestsTotal.add(
                   1L,
                   Attribute("http.method", req.method.name),
                   Attribute("http.status_code", resp.status.code.toString)
                 )
        _     <- metrics.httpRequestDuration.record(
                   ms,
                   Attribute("http.method", req.method.name),
                   Attribute("http.route", req.pathInfo.renderString)
                 )
      yield resp
    }
