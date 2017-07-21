package org.skramer.learn.eip

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe
import org.skramer.learn.AkkaLearningTestTrait

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationLong, FiniteDuration}

class ScatterActor(receiversList: List[ActorRef]) extends Actor {
  override def receive: Receive = {
    case msg => receiversList.foreach { _ ! msg }
  }
}

case class ScatteredData(id: Int, content: Option[String], attributes: Option[List[String]])

class GatherActor(next: ActorRef, timeout: FiniteDuration) extends Actor {

  private var buffer = Vector[ScatteredData]()
  private implicit val ec: ExecutionContext = context.system.dispatcher

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    buffer.foreach { d => self ! d }
    super.preRestart(reason, message)
  }

  override def receive: Receive = {
    case msg@ScatteredData(id, content, attributes) => buffer.find { _.id == id } match {
      case Some(data) => next ! ScatteredData(id, data.content.orElse(content), data.attributes.orElse(attributes))
      case None => buffer = buffer :+ msg
        context.system.scheduler.scheduleOnce(timeout, self, TimeoutMessage(msg.id))
    }
    case TimeoutMessage(id) => buffer.find { _.id == id } match {
      case Some(data) => next ! data
      case None =>
    }
    case "throw" => throw new IllegalStateException("manual actor restart")
  }

  case class TimeoutMessage(id: Int)

}

object GatherActor {
  def props(next: ActorRef, timeout: FiniteDuration = 1 second) = Props(new GatherActor(next, timeout))
}

object ScatterActor {
  def prop(receiversList: List[ActorRef]): Props = Props(new ScatterActor(receiversList))
}

class ScatterGatherTest extends AkkaLearningTestTrait {
  "scatter actor" should {
    "send received messages to all receivers" in {
      val actorProbes = List.fill(5)(TestProbe())
      val scatterActor = system.actorOf(ScatterActor.prop(actorProbes.map { _.ref }))
      scatterActor ! "testMessage"
      actorProbes.foreach { probe => probe.expectMsg("testMessage") }
    }
  }

  "gather actor" should {
    "combine multiple messages with the same ID" in {
      val probe = TestProbe()
      val gather = system.actorOf(GatherActor.props(probe.ref))

      gather ! ScatteredData(1, Some("hello"), None)
      gather ! ScatteredData(1, None, Some(List("attr")))

      probe.expectMsg(ScatteredData(1, Some("hello"), Some(List("attr"))))
    }

    "send buffered messages after specified timeout" in {
      val probe = TestProbe()
      val gather = system.actorOf(GatherActor.props(probe.ref))

      gather ! ScatteredData(1, Some("hello"), None)

      probe.expectMsg(ScatteredData(1, Some("hello"), None))
    }

    "restore messages after restart" in {
      val probe = TestProbe()
      val gather = system.actorOf(GatherActor.props(probe.ref))

      gather ! ScatteredData(1, Some("hello"), None)

      gather ! "throw"

      gather ! ScatteredData(1, None, Some(List("attr")))

      probe.expectMsg(ScatteredData(1, Some("hello"), Some(List("attr"))))
    }
  }

  class AttributesGenerator(next: ActorRef, fakeAttributes: List[String]) extends Actor {
    override def receive: Receive = {
      case msg@ScatteredData(_, _, None) => next ! msg.copy(attributes = Some(fakeAttributes))
    }
  }

  class ContentGenerator(next: ActorRef, fakeContent: String) extends Actor {
    override def receive: Receive = {
      case msg@ScatteredData(_, None, _) => next ! msg.copy(content = Some(fakeContent))
    }
  }

  "scatter and gather actors" should {
    "cooperate when combined" in {

      val probe = TestProbe()
      val gatherActor = system.actorOf(GatherActor.props(probe.ref))
      val contentGenerator = system.actorOf(Props(new ContentGenerator(gatherActor, "fake")))
      val attibutesGenerator = system.actorOf(Props(new AttributesGenerator(gatherActor, List("attr"))))
      val receiversList = List(contentGenerator, attibutesGenerator)
      val scatterActor = system.actorOf(ScatterActor.prop(receiversList))

      scatterActor ! ScatteredData(1, None, None)

      probe.expectMsg(ScatteredData(1, Some("fake"), Some(List("attr"))))
    }
  }
}
