package homework

import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId

object ReadActorProcess {

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { ctx => Behaviors.same
    }

  def main(args: Array[String]): Unit = {
    val persId = PersistenceId.ofUniqueId("001")

    implicit val system: ActorSystem[NotUsed] = ActorSystem(ReadActorProcess(), "readActorProcess")

    TypedCalculatorReadSide(system, persId)

    implicit val executionContext = system.executionContext
  }
}
