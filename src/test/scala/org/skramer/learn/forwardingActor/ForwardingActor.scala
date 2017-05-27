package org.skramer.learn.forwardingActor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing

class AkkaTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "forwarding actor" should {
    "forward all messages it receives" in {
      import ForwardingActor._
      val actor = system.actorOf(ForwardingActor.props(testActor))
      actor ! Message
      expectMsgClass(Message.getClass)
    }
  }
}

object ForwardingActor {
  def props(forwardingTarget: ActorRef): Props = Props(new ForwardingActor(forwardingTarget))

  case class Message()

}

class ForwardingActor(forwardingTarget: ActorRef) extends Actor {
  override def receive: Receive = {
    case msg: Any => forwardingTarget forward msg
  }
}


