package org.skramer.learn.actorTermination

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing

class ActorTerminationTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "monitoring actor" should {
    "get notified about monitored actor's death" in {
      val killedActor = system.actorOf(Props[KilledActor])
      val watchingActor = system.actorOf(Props(classOf[WatchingActor], killedActor, testActor))

      killedActor ! PoisonPill

      expectMsg(ActorTerminated)

    }
  }
}

class WatchingActor(actorToWatch: ActorRef, receiver: ActorRef) extends Actor {
  context.watch(actorToWatch)
  override def receive: Receive = {
    case Terminated(killedActor) => receiver ! ActorTerminated
  }
}

class KilledActor extends Actor {
  override def receive: Receive = {
    case msg: Any =>
  }
}

case class ActorTerminated()
