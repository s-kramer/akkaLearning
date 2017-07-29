package org.skramer.learn.routers

import akka.actor.{Actor, ActorRef, Props}
import akka.routing.ConsistentHashingRouter.{ConsistentHashMapping, ConsistentHashable, ConsistentHashableEnvelope}
import akka.routing._
import akka.testkit.TestProbe
import org.skramer.learn.AkkaLearningTestTrait
import org.skramer.learn.forwardingActor.ForwardingActor

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class RoutersTest extends AkkaLearningTestTrait {
  "router created from source code" should {
    "spawn its children and send messages to them" in {
      val probe = TestProbe()

      val router = system.actorOf(RoundRobinPool(5).props(Props(new ForwardingActor(probe.ref))), "roundRobinRouter")

      router ! 1
      router ! 2
      router ! 3
      router ! 4
      router ! 5

      val senders = ListBuffer[ActorRef]()
      val numbers = ListBuffer[Int]()
      for (i <- 1 to 5) {
        probe.expectMsgPF() {
          case msg: Int => senders += probe.lastSender; numbers += msg
        }
      }
      numbers should contain allElementsOf (1 to 5)
      senders should have size 5
    }
  }

  "any router" should {
    "propagate broadcast to all routees" in {
      val probes = List.fill(5) { TestProbe() }

      val router = system.actorOf(RoundRobinGroup(probes.map { _.ref.path.toString }).props(), "roundRobinGroupRouter")

      router ! Broadcast(1)

      probes.foreach { p => p.expectMsg(1) }
    }
  }

  "broadcasting router " should {
    "send every message to all the routees" in {
      val probe1 = TestProbe("probe1")
      val probe2 = TestProbe("probe2")
      val path = probe1.ref.path

      val router = system.actorOf(BroadcastGroup(List(probe1.ref.path.toString, probe2.ref.path.toString))
                                  .props(), "broadcastingGroup")

      router ! 1
      router ! 2

      val probe1Msgs = probe1.receiveN(2)
      val probe2Msgs = probe2.receiveN(2)
      probe1Msgs should contain allElementsOf (1 to 2)
      probe2Msgs should contain allElementsOf (1 to 2)
    }
  }

  class HardWorkingActor extends Actor {
    val random = scala.util.Random

    implicit val ec: ExecutionContext = system.dispatcher

    override def receive: Receive = {
      case number: Int => system.scheduler.scheduleOnce(random.nextInt(20) milliseconds, self, (number, sender()))
      case (number, sender: ActorRef) => sender ! number
    }
  }

  "router with resizer" should {
    "spawn additional children when under pressure" in {
      val probe = TestProbe()

      val router = system.actorOf(RoundRobinPool(1)
                                  .withResizer(new DefaultResizer(lowerBound = 1, upperBound = 20, pressureThreshold = 1,
                                    backoffRate = 0, // disable pool-actor destruction just for the sake of the test
                                    rampupRate = 0.25, messagesPerResize = 10))
                                  .props(Props(new HardWorkingActor)), "roundRobinWithResizer")

      val msgCount = 100000
      for (i <- 1 to msgCount) {
        router.tell(i, probe.ref)
      }

      var senders = Set[ActorRef]()
      for (_ <- 1 to msgCount) {
        probe.expectMsgPF() { case _ => senders += probe.lastSender }
      }

      senders should have size 20

    }
  }

  "router created from configuration" should {
    "spawn its children and send messages to them" in {
      val probe = TestProbe()

      val router = system.actorOf(FromConfig().props(Props(new ForwardingActor(probe.ref))), "balancingPool")

      router ! 1
      router ! 2
      router ! 3
      router ! 4
      router ! 5

      val receivedMessages = probe.receiveN(5)
      receivedMessages.map(_.asInstanceOf[Int]) should contain allElementsOf (1 to 5)
    }
  }

  case class IdentifiableMessage(id: Int, content: String)

  case class IdentifiableMessageWithHash(id: Int, content: String) extends ConsistentHashable {
    override def consistentHashKey: Any = id + content
  }

  case class IdentifiableMessageForEnvelope(id: Int, content: String)

  case class GatheredResponse(id: Int, content: String)

  "consistently hashing router" should {
    "use the same routee for similar messages" in {

      def hashingFunction: ConsistentHashMapping = {
        case IdentifiableMessage(id, _) => id
      }

      class GatheringActor(next: ActorRef) extends Actor {
        private var buffer = Map[Int, String]()

        override def receive: Receive = {
          case IdentifiableMessage(id, content) => bufferOrSend(id, content)
          case IdentifiableMessageWithHash(id, content) => bufferOrSend(id, content)
          case IdentifiableMessageForEnvelope(id, content) => bufferOrSend(id, content)
        }

        private def bufferOrSend(id: Int, content: String) = {
          buffer.get(id) match {
            case Some(old_content) => next ! GatheredResponse(id, content = old_content + content)
              buffer -= id
            case None => buffer += (id -> content)
          }
        }
      }

      val probe = TestProbe()
      val router = system.actorOf(ConsistentHashingPool(10, hashMapping = hashingFunction)
                                  .props(Props(new GatheringActor(probe.ref))))

      router ! IdentifiableMessage(1, "First")
      router ! IdentifiableMessage(2, "Second")

      router ! IdentifiableMessage(1, "Third")
      probe.expectMsg(GatheredResponse(1, "FirstThird"))

      router ! IdentifiableMessage(2, "Fourth")
      probe.expectMsg(GatheredResponse(2, "SecondFourth"))


      router ! IdentifiableMessageWithHash(5, "Fifth")
      router ! IdentifiableMessageWithHash(6, "Sixth")
      router ! IdentifiableMessageWithHash(5, "Fifth")
      router ! IdentifiableMessageWithHash(6, "Sixth")
      probe
      .expectMsgPF() { case GatheredResponse(5, concatenatedContents) => concatenatedContents shouldBe "FifthFifth" }

      probe
      .expectMsgPF() { case GatheredResponse(6, concatenatedContents) => concatenatedContents shouldBe "SixthSixth" }

      router ! ConsistentHashableEnvelope(IdentifiableMessageForEnvelope(999, "Seventh"), 7)
      router ! ConsistentHashableEnvelope(IdentifiableMessageForEnvelope(1000, "Eight"), 8)
      router ! ConsistentHashableEnvelope(IdentifiableMessageForEnvelope(999, "Seventh"), 7)
      router ! ConsistentHashableEnvelope(IdentifiableMessageForEnvelope(1000, "Eight"), 8)

      probe
      .expectMsgPF() { case GatheredResponse(999, concatenatedContents) => concatenatedContents shouldBe "SeventhSeventh" }

      probe
      .expectMsgPF() { case GatheredResponse(1000, concatenatedContents) => concatenatedContents shouldBe "EightEight" }
    }
  }
}
