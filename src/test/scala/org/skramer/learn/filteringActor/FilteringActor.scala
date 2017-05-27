package org.skramer.learn.filteringActor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing
import org.skramer.learn.filteringActor.FilteringActor.{Filtered, Unfiltered}

import scala.concurrent.duration.DurationLong

class AkkaTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "stateless filtering actor" should {
    "filter out the duplicates" in {
      import FilteringActor._

      val filteringActor = system.actorOf(props(testActor), "duplicatesRemovingActor")
      val valuesWithDuplicates = Vector(1, 1, 2, 3, 3, 4, 5, 5, 5)
      filteringActor ! Unfiltered(valuesWithDuplicates)

      expectMsgPF() {
        case Filtered(values) => containsDuplicates(values) shouldBe false
      }
    }

    def containsDuplicates(v: Vector[Int]): Boolean = {
      def helper(visited: Set[Int], remaining: Vector[Int]): Boolean = remaining match {
        case Vector() => false
        case Vector(x) => visited contains x
        case x +: tail => if (visited contains x) true else helper(visited + x, tail)
      }

      helper(Set(), v)
    }

  }

  "stateful filtering actor" should {
    "keep filtered state" in {
      import StatefulFilteringActor._
      val stateFulFilteringActor = system.actorOf(StatefulFilteringActor.prop(testActor))

      stateFulFilteringActor ! Event(1)
      stateFulFilteringActor ! Event(1)
      stateFulFilteringActor ! Event(2)
      stateFulFilteringActor ! Event(3)
      stateFulFilteringActor ! Event(2)
      stateFulFilteringActor ! Event(3)
      stateFulFilteringActor ! Event(4)
      stateFulFilteringActor ! Event(1)
      stateFulFilteringActor ! Event(2)
      stateFulFilteringActor ! Event(5)
      stateFulFilteringActor ! Event(5)
      stateFulFilteringActor ! Event(6)

      val receivedIds = receiveWhile() {
        case Event(id) if id <= 5 => id
      }
      receivedIds shouldBe Vector(1, 2, 3, 4, 5)
      expectMsg(Event(6))

      stateFulFilteringActor ! Event(1)
      expectNoMsg(10 millisecond)
      stateFulFilteringActor ! Event(2)
      expectNoMsg(10 millisecond)
      stateFulFilteringActor ! Event(3)
      expectNoMsg(10 millisecond)
    }
  }

}


object FilteringActor {
  def props(responseReceiver: ActorRef): Props = Props(new FilteringActor(responseReceiver))


  case class Unfiltered(values: Vector[Int])

  case class Filtered(values: Vector[Int])

}

class FilteringActor(responseReceiver: ActorRef) extends Actor {
  override def receive: Receive = {
    case Unfiltered(values) => responseReceiver ! Filtered(values.distinct)
  }
}

object StatefulFilteringActor {

  case class Event(id: Int)

  def prop(receiver: ActorRef): Props = Props(new StatefulFilteringActor(receiver))

  class StatefulFilteringActor(receiver: ActorRef) extends Actor {
    private var messages = Vector[Event]()

    override def receive: Receive = {
      case msg: Event =>
        if (!messages.contains(msg)) {
          messages :+= msg
          receiver ! msg
        }
    }
  }

}
