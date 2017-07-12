import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.Keys._
import sbt.{Def, _}

object MultiJvmConfiguration extends Build {

  lazy val buildSettings: Seq[Def.Setting[_]] = Defaults.coreDefaultSettings ++ multiJvmSettings ++ Seq(
    crossPaths := false
  )

  lazy val goticks: Project = Project(
    id = "goticks",
    base = file("."),
    settings = buildSettings ++ Defaults.coreDefaultSettings
  ) configs MultiJvm

  lazy val multiJvmSettings: Seq[Setting[_]] = SbtMultiJvm.multiJvmSettings ++ Seq(
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiJvmResults) =>
        val overall =
          if (testResults.overall.id < multiJvmResults.overall.id) multiJvmResults.overall
          else testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiJvmResults.events,
          testResults.summaries ++ multiJvmResults.summaries)
    }
  )
}

