package org.skramer.learn

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

trait STMultiNodeSpec extends MultiNodeSpecCallbacks
                              with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = multiNodeSpecAfterAll()
}
