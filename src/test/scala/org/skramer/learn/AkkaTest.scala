package org.skramer.learn

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{Matchers, WordSpecLike}

class AkkaTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "sanity test" should {
    "pass" in {
      2 shouldBe 2
    }
  }
}




