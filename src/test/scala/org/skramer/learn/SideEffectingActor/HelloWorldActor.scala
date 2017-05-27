package org.skramer.learn.SideEffectingActor

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, EventFilter, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing
import org.skramer.learn.SideEffectingActor.HelloWorldActor.GreeterMessage

class HelloWorldActorTest extends TestKit(HelloWorldActor
                                          .loggingActorSystem) with WordSpecLike with Matchers with AkkaSystemClosing {
  "logging actor" should {
    "send log messages" in {
      import HelloWorldActor._
      val props = Props[HelloWorldActor].withDispatcher(CallingThreadDispatcher.Id)
      val actor = system.actorOf(props)
      EventFilter.info(message = "Hello World", occurrences = 1).intercept {
        actor ! GreeterMessage("World")
      }
    }
  }
}

object HelloWorldActor {

  case class GreeterMessage(msg: String)

  val loggingActorSystem = ActorSystem("loggingActorSystem", config = ConfigFactory.parseString(
    """
      |akka.loggers = [akka.testkit.TestEventListener]
    """.stripMargin))
}

class HelloWorldActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case GreeterMessage(who) => log.info("Hello {}", who)
  }
}

