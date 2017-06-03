package org.skramer.learn.supervisorStrategies

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, DeadLetter, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing

class SupervisorStrategiesTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {

  import StateHoldingActor._

  "/user guardian with default supervision strategy" should {
    "restart actor that crashed" in {
      val stateHoldingActor = system.actorOf(StateHoldingActor.props(testActor))
      stateHoldingActor ! AddStateCommand(5)
      stateHoldingActor ! GetStateCommand()
      expectMsg(Vector(5))
      stateHoldingActor ! ActorThrowCommand()
      stateHoldingActor ! GetStateCommand()
      expectMsg(Vector())
    }

    "kill actor that crashed during initialization" in {
      val deadLetterRecipient = TestProbe("deadLetterRecipient")
      system.eventStream.subscribe(deadLetterRecipient.testActor, classOf[DeadLetter])
      val nonInitializableActor = system.actorOf(Props(new NonInitializableActor()))
      nonInitializableActor ! "you won't get this"
      deadLetterRecipient.expectMsgClass(classOf[DeadLetter])
    }
  }


}

object StateHoldingActor {

  case class ActorThrowCommand()

  case class AddStateCommand(stateElement: Int)

  case class GetStateCommand()

  def props(receiver: ActorRef): Props = Props(new StateHoldingActor(receiver))
}

class StateHoldingActor(receiver: ActorRef) extends Actor with ActorLogging {
  log.info("about to create state")
  private var state = Vector[Int]()
  log.info(s"state created: $state")

  import StateHoldingActor._

  override def receive: Receive = {
    case AddStateCommand(i) =>
      log.info(s"extending state: $state")
      state = i +: state
      log.info(s"extended state: $state")
    case GetStateCommand() =>
      log.info(s"returning state: $state")
      receiver ! state
    case _: ActorThrowCommand =>
      log.info(s"throwing exception with state: $state")
      throw new IllegalStateException("Should crash actor instance and restart state")

  }

}

class NonInitializableActor() extends Actor {

  throw new IllegalStateException("should fail at initialization")

  override def receive: Receive = {
    case _ =>
  }

}
