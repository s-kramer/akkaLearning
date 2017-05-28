package org.skramer.learn.actorLifecycle

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing

case class ActorCreated()

case class StartCalled()

case class StopCalled()

case class PreRestartCalled()

case class PostRestartCalled()

class LifecycleTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "actor's start and stop hooks" should {
    "be called at creation and destruction" in {
      val lifecycleActor = system.actorOf(LifecycleActor.props(testActor))
      expectMsg(ActorCreated)
      expectMsg(StartCalled)
      lifecycleActor ! PoisonPill
      expectMsg(StopCalled)
    }
  }

  "supervising actor" should {
    "be able to restart child actor" in {
      val restartingActor = system.actorOf(RestartingActor.props(testActor))
      expectMsg(ActorCreated)
      expectMsg(StartCalled)

      restartingActor ! "crash"
      expectMsg(PreRestartCalled)
      expectMsg(StopCalled)
      expectMsg(ActorCreated)
      expectMsg(PostRestartCalled)
      expectMsg(StartCalled)
    }
  }
}

object LifecycleActor {
  def props(receiver: ActorRef): Props = Props(new LifecycleActor(receiver))
}

class LifecycleActor(receiver: ActorRef) extends Actor {
  receiver ! ActorCreated

  override def preStart(): Unit = {
    receiver ! StartCalled
    super.preStart()
  }


  override def postStop(): Unit = {
    receiver ! StopCalled
    super.postStop()
  }

  override def receive: Receive = {
    case msg =>
  }
}

object RestartingActor {
  def props(receiver: ActorRef): Props = Props(new RestartingActor(receiver))
}

class RestartingActor(receiver: ActorRef) extends Actor {
  receiver ! ActorCreated

  override def receive: Receive = {
    case "crash" => throw new IllegalStateException("restart forced")
    case msg: Any => sender() ! msg
  }

  override def preStart(): Unit = {
    receiver ! StartCalled
    super.preStart()
  }

  override def postStop(): Unit = {
    receiver ! StopCalled
    super.postStop()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    receiver ! PreRestartCalled
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    receiver ! PostRestartCalled
    super.postRestart(reason)
  }
}
