package homework

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.typed.PersistenceId
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import homework.TypedCalculatorWriteSide.{Added, Divided, Multiplied}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

case class TypedCalculatorReadSide(system: ActorSystem[NotUsed], persId: PersistenceId) {

  implicit val materializer = system.classicSystem

  implicit val session: SlickSession = SlickSession.forConfig("slick-postgres")
  import session.profile.api._

    val result: Future[(Int, Double)] =
      Slick
        .source(
          sql"""select
                    write_side_offset,
                    calculated_value
                from
                    public.result
                where
                    id = 1""".
            as[(Int, Double)])
        .runWith(Sink.head)

  var (offset, latestCalculatedResult) = Await.result(result, 10.seconds)

  val startOffset: Int = if (offset == 1) 1 else offset + 1

  val readJournal: CassandraReadJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val source: Source[EventEnvelope, NotUsed] = readJournal
    .eventsByPersistenceId(persId.id, startOffset, Long.MaxValue)

  val updateState: Flow[EventEnvelope, EventEnvelope, NotUsed] = Flow[EventEnvelope].map { event =>
    event.event match {
      case Added(_, amount) =>
        latestCalculatedResult += amount
        println(s"! Log from update state and match to Added: $latestCalculatedResult")
      case Multiplied(_, amount) =>
        latestCalculatedResult *= amount
        println(s"! Log from update state and match to Multiplied: $latestCalculatedResult")
      case Divided(_, amount) =>
        latestCalculatedResult /= amount
        println(s"! Log from update state and match to Divided: $latestCalculatedResult")
    }
    event
  }

  val updateResultInDb: Flow[EventEnvelope, Int, NotUsed] = Slick.flow(event =>
    sqlu"""update
               public.result
           set
               calculated_value=${latestCalculatedResult},
               write_side_offset=${event.sequenceNr}
           where id = 1"""
  )

    source.async
      .via(updateState).async
      .via(updateResultInDb).async
      .runWith(Sink.ignore)
}
