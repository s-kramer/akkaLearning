package distributed

import com.typesafe.config._
import akka.actor._
import distributed.DistributedHelloWorld.{backendConf, frontendConf}

object DistributedHelloWorld {
  val frontendConf: String = generateConfigTemplate(port = 2552)
  val backendConf: String = generateConfigTemplate(port = 2553)

  private def generateConfigTemplate(port: Int): String = {
    s"""akka {
        actor {
          provider = "akka.remote.RemoteActorRefProvider"
        }
        remote {
          enabled-transports = ["akka.remote.netty.tcp"]
          netty.tcp {
              hostname = "0.0.0.0"
              port = $port
          }
        }
}"""
  }
}

object Backend extends App {
  val config = ConfigFactory.parseString(backendConf)
  val sys = ActorSystem("backend", config)
  val ref = sys.actorOf(Props[EchoActor], "echoActor")
}

object Frontend extends App {
  val config = ConfigFactory.parseString(frontendConf)
  val sys = ActorSystem("frontend", config)
  val echoActorPath = "akka.tcp://backend@0.0.0.0:2553/user/echoActor"
  val ref = sys.actorSelection(echoActorPath)
  ref ! "Hello, World!"
}

class EchoActor extends Actor {
  override def receive: Receive = {
    case msg => println(s"Received $msg")
  }
}
