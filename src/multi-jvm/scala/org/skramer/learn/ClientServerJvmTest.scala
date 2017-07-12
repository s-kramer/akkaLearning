package org.skramer.learn

import akka.actor.{Actor, ActorIdentity, Identify, Props}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender

class ClientServerJvmTestMultiJvmFrontend extends ClientServerJvmTest

class ClientServerJvmTestMultiJvmBackend extends ClientServerJvmTest

class RespondingActor extends Actor {
  override def receive: Receive = {
    case x: Int => sender() ! x * 2
  }
}

class ClientServerJvmTest extends MultiNodeSpec(ClientServerConfig) with STMultiNodeSpec with ImplicitSender {

  import ClientServerConfig._

  private val backendNode = node(backend) // finds the random address assigned to this node, must be called from main thread

  override def initialParticipants: Int = roles.size

  "A client server configured app" should {
    "wait for all nodes to become ready" in {
      enterBarrier("startup")
    }

    "be able to identify an actor and sent a message" in {
      runOn(backend) {
        system.actorOf(Props[RespondingActor], "responder")
        enterBarrier("deployed")
      }

      runOn(frontend) {
        enterBarrier("deployed")

        Thread.sleep(1000)
        val path = node(backend) / "user" / "responder"
        val actorSelection = system.actorSelection(path)

        actorSelection ! Identify(path)

        val backendRef = expectMsgPF() {
          case ActorIdentity(`path`, Some(ref)) => ref
        }

        backendRef ! 222

        expectMsg(444)
      }

      enterBarrier("finished")
    }
  }
}
