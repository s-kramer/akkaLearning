package org.skramer.learn.stateHoldingActor

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing
import org.skramer.learn.stateHoldingActor.StateHoldingActor.{DiscloseLastMessage, StringMessage}

object StateHoldingActor {

  // messages used by the actor
  case class StringMessage(msg: String)

  case class DiscloseLastMessage(receiver: ActorRef)

}

// must be defined outside of any class and trait
class StateHoldingActor extends Actor {
  var lastMessage: String = ""

  override def receive: Receive = {
    case msg: String => lastMessage = msg
    case StringMessage(msg) => lastMessage = msg
    // to be used in tests only, not really cool
    case DiscloseLastMessage(ref) => ref ! StringMessage(lastMessage)
  }
}

class AkkaTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "state holding actor" should {
    "keep state from last message" in {
      val actor = TestActorRef[StateHoldingActor]
      actor ! "Hello"
      actor.underlyingActor.lastMessage shouldBe "Hello"
    }

    "accept messages defined in companion object" in {
      val actor = TestActorRef[StateHoldingActor]
      actor ! StringMessage("World")
      actor.underlyingActor.lastMessage shouldBe "World"
    }

    "disclose its state when asked" in {
      val actor = TestActorRef[StateHoldingActor]
      actor.underlyingActor.lastMessage shouldBe ""
      actor ! StringMessage("Hello world")
      actor ! DiscloseLastMessage(testActor)

      expectMsg(StringMessage("Hello world"))
    }

  }
}
