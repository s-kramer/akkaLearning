package org.skramer.learn.supervisorStrategies

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, AllForOneStrategy, DeadLetter, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.ask
import akka.testkit.{CallingThreadDispatcher, ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing
import org.skramer.learn.supervisorStrategies.StateHoldingActor.{ActorThrowCommand, AddStateCommand, GetStateCommand}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class SupervisorStrategiesTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with ImplicitSender with AkkaSystemClosing {

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

  "actor with custom supervision strategy" should {
    "apply the strategy to a single child" in {
      implicit val timeout: Timeout = 3 seconds

      val parentActor = system.actorOf(Props(new OneForOneParentActor(testActor)))

      val initialStateFuture = parentActor ? "state"
      val initialState = Await.result(initialStateFuture, timeout.duration)
      initialState shouldBe List(Vector(), Vector())

      parentActor ! ("first", AddStateCommand(1))
      parentActor ! ("second", AddStateCommand(2))

      val currentStateFuture = parentActor ? "state"
      val currentState = Await.result(currentStateFuture, timeout.duration)
      currentState shouldBe List(Vector(1), Vector(2))

      parentActor ! "throwFirst"

      val stateAfterRestartFuture = parentActor ? "state"
      val stateAfterRestart = Await.result(stateAfterRestartFuture, timeout.duration)
      stateAfterRestart shouldBe List(Vector(), Vector(2))
    }

    "apply the strategy to all children" in {
      implicit val timeout: Timeout = 3 seconds

      val parentActor = system.actorOf(Props(new AllForOneParentActor(testActor)))

      val initialStateFuture = parentActor ? "state"
      val initialState = Await.result(initialStateFuture, timeout.duration)
      initialState shouldBe List(Vector(), Vector())

      parentActor ! ("first", AddStateCommand(1))
      parentActor ! ("second", AddStateCommand(2))

      val currentStateFuture = parentActor ? "state"
      val currentState = Await.result(currentStateFuture, timeout.duration)
      currentState shouldBe List(Vector(1), Vector(2))

      parentActor ! "throwFirst"

      val stateAfterRestartFuture = parentActor ? "state"
      val stateAfterRestart = Await.result(stateAfterRestartFuture, timeout.duration)
      stateAfterRestart shouldBe List(Vector(), Vector())
    }
  }


}

object StateHoldingActor {

  case class ActorThrowCommand()

  case class AddStateCommand(stateElement: Int)

  case class GetStateCommand()

  case class GetStateCommandWithResponse()

  def props(receiver: ActorRef): Props = Props(new StateHoldingActor())
}

class StateHoldingActor() extends Actor with ActorLogging {
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
      sender ! state
    case GetStateCommandWithResponse() =>
      log.info(s"returning state in response: $state")
      sender ! state
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

abstract class ParentActor(recipient: ActorRef) extends Actor with ActorLogging {
  log.info("creating children")
  private val stateHoldingActor1 = context
                                   .actorOf(Props(new StateHoldingActor()).withDispatcher(CallingThreadDispatcher.Id))
  private val stateHoldingActor2 = context
                                   .actorOf(Props(new StateHoldingActor()).withDispatcher(CallingThreadDispatcher.Id))
  log.info("children created")

  implicit val timeout: Timeout = 3 seconds

  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Receive = {
    case "throwFirst" =>
      log.info("stateHoldingActor1 ! ctorThrowCommand")
      stateHoldingActor1 ! ActorThrowCommand()
    case "throwSecond" =>
      log.info("stateHoldingActor1 ! ActorThrowCommand")
      stateHoldingActor2 ! ActorThrowCommand()
    case "state" =>
      log.info("gathering states")
      val futureResults: Future[List[Any]] = Future
                                             .sequence(List(stateHoldingActor1 ? GetStateCommand(), stateHoldingActor2 ? GetStateCommand()))
      import akka.pattern.pipe
      futureResults pipeTo sender()

    case ("first", msg@AddStateCommand(_)) => stateHoldingActor1 forward msg
    case ("second", msg@AddStateCommand(_)) => stateHoldingActor2 forward msg
  }
}

class OneForOneParentActor(recipient: ActorRef) extends ParentActor(recipient) {
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => Restart
  }
}

class AllForOneParentActor(recipient: ActorRef) extends ParentActor(recipient) {
  override def supervisorStrategy: SupervisorStrategy = AllForOneStrategy() {
    case _ => Restart
  }
}
