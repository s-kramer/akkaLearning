package org.skramer.learn.eip

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe
import org.skramer.learn.AkkaLearningTestTrait

class FilteringActor[T](pipe: ActorRef, predicate: (T => Boolean)) extends Actor {
  override def receive: Receive = {
    case msg: T => if (predicate(msg)) pipe ! msg
  }
}

object FilteringActor {
  def prop[T](pipe: ActorRef, predicate: (T => Boolean)) = Props(FilteringActor(pipe, predicate))

  def apply[T](pipe: ActorRef, predicate: (T => Boolean)): FilteringActor[T] = new FilteringActor(pipe, predicate)
}

case class Data(id: Int, content: String)

class FilteringTests extends AkkaLearningTestTrait {
  "filtering actor" should {
    "pass only matching messages" in {
      val probe = TestProbe()
      val ref = system
                .actorOf(FilteringActor.prop[Data](probe.ref, { case Data(_, content) => content contains "valid" }))
      ref ! Data(1, "this message is to be ignored")
      ref ! Data(2, "this message is valid")

      probe.expectMsgPF() { case Data(id, _) => id shouldBe 2 }
    }

    "be concatenable with other actor accepting the same data type" in {
      val probe = TestProbe()
      val filter2 = system
                    .actorOf(FilteringActor.prop[Data](probe.ref, { case Data(id, _) => id % 2 == 0 }))
      val filter1 = system
                    .actorOf(FilteringActor.prop[Data](filter2, { case Data(_, content) => content contains "valid" }))

      filter1 ! Data(2, "this message has correct id is to be ignored because of the content")
      filter1 ! Data(3, "this message is valid but contains incorrect id")
      filter1 ! Data(4, "this message is valid")

      probe.expectMsgPF() { case Data(id, _) => id shouldBe 4 }
    }
  }
}
