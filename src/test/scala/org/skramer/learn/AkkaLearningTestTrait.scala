package org.skramer.learn

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{Matchers, Suite, WordSpecLike}

class AkkaLearningTestTrait extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  this: TestKit with Suite =>

}
