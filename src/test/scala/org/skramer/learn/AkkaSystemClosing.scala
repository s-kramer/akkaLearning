package org.skramer.learn

import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Suite}

trait AkkaSystemClosing extends BeforeAndAfterAll {
  this: TestKit with Suite =>

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }
}
