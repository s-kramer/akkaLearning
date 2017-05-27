package org.skramer.learn.sendingActor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing
import org.skramer.learn.sendingActor.SendingActor.{GetMax, MaxOfCollection}

import scala.util.Random

object SendingActor {

  case class GetMax(valuesToSearch: TraversableOnce[Int])

  case class MaxOfCollection(max: Int)

  def props(actor: ActorRef): Props = Props(classOf[SendingActor], actor)
}

class SendingActor(responseReceiver: ActorRef) extends Actor {

  override def receive: Receive = {
    case GetMax(values) => responseReceiver ! MaxOfCollection(values.max)
  }
}
class AkkaTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {

  "message sending actor" should {
    "respond to messages" in {
      import org.skramer.learn.sendingActor.SendingActor._
      val actor = system.actorOf(props(testActor))
      val valuesToSearch = Vector.range(1, 1000)
      actor ! GetMax(Random.shuffle(valuesToSearch))
      expectMsgPF() {
        case MaxOfCollection(max) => max shouldBe 999
      }
    }
  }
}
