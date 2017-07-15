package org.skramer.learn.logging

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class LoggingActor extends Actor {
  private val logger = Logging.getLogger(context.system, this)

  override def receive: Receive = {
    case msg => logger.info("Msg received: {}", msg)
  }
}

class LoggingTest extends WordSpec with Matchers {
  "logging actor" should {
    "log every message" in {
      val system = ActorSystem("loggingTest", ConfigFactory.load("logging"))
      val actorRef = system.actorOf(Props[LoggingActor])
      actorRef ! "Hello logging world!"
    }
  }
}
