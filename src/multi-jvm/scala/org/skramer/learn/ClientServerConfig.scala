package org.skramer.learn

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig

object ClientServerConfig extends MultiNodeConfig {
  val frontend: RoleName = role("frontend")
  val backend: RoleName = role("backend")


}
