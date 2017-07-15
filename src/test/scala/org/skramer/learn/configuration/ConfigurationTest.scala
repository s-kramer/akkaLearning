package org.skramer.learn.configuration

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._

class ConfigurationTest extends fixture.WordSpec with Matchers {
  override type FixtureParam = Config

  "config factory" should {
    "read config" in { cf =>
      cf.getString("AkkaLearning.envVar") shouldBe "skramer"
    }

    "read defaults from fallback config" in { cf =>
      cf.getString("referenceConfigVar") shouldBe "reference.conf"
    }

    "read config subtree" in { cf =>
      val nestedConfig = cf.getConfig("AkkaLearning.Nested")
      nestedConfig.getString("otherVar") shouldBe "skramer"
    }

    "read included conf" in { cf =>
      val includedIntValue = cf.getInt("Included.IntValue")
      includedIntValue shouldBe 20
    }

    "read overridden attributes" in { cf =>
      val overridenValue = cf.getString("toBeOverridden")
      overridenValue shouldBe "thisIsAnOverride"
    }

    "read overridden attributes from included file" in { cf =>
      val overridenValue = cf.getString("toBeOverriddenFromInclude")
      overridenValue shouldBe "thisIsAnOverrideOfIncludedProperty"
    }

    "read overridden attributes from lifted config" in { cf =>
      val liftedConfigWithFallback = cf.getConfig("SubApp").withFallback(cf)
      liftedConfigWithFallback.getString("AkkaLearning.envVar") shouldBe "skramer"
      liftedConfigWithFallback.getString("AkkaLearning.Nested.otherVar") shouldBe "overrideFromLiftedConf"
    }

  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    val config = ConfigFactory.load("myApplicationConf")
    val system = ActorSystem("testSystem", config)

    withFixture(test.toNoArgTest(system.settings.config))
  }
}
