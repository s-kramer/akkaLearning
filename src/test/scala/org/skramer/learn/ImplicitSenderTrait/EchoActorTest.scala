package org.skramer.learn.ImplicitSenderTrait

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing

class EchoActorTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with ImplicitSender with AkkaSystemClosing {
  "echo actor" should {
    "respond to the implicitly set sender" in {
      import EchoActor._
      val echoActor = system.actorOf(Props[EchoActor])
      echoActor ! EchoedMessage("content")
      expectMsg(EchoedMessage("content"))
    }
  }

}

class EchoActor extends Actor {
  override def receive: Receive = {
    case msg => sender() ! msg
  }
}

object EchoActor{

  case class EchoedMessage(content: String)

}
