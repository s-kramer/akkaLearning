package org.skramer.learn.futuresTest

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestKitExtension}
import org.scalatest.{Matchers, WordSpecLike}
import org.skramer.learn.AkkaSystemClosing

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class FuturesTest extends TestKit(ActorSystem("testSystem")) with WordSpecLike with Matchers with AkkaSystemClosing {
  "it" should {
    "be possible to select first future" in {
      val f1 = Future {
        Thread
        .sleep(100)
        5
      }
      val f2 = Future {
        Thread
        .sleep(200)
        15
      }
      val f3 = Future {
        Thread
        .sleep(300)
        25
      }
      val fastest = Future
                    .firstCompletedOf(Vector(f2, f3, f1))
      fastest
      .onComplete {
        case Success(v) => v shouldBe 5
        case Failure(e) => fail(s"failed with $e")
      }
    }

    "be possible to wait for a success and ignore failures that are returned faster" in {
      val f1 = Future.failed(new RuntimeException("fail fast"))
      val f2 = Future {
        Thread.sleep(100)
        15
      }
      val f3 = Future {
        Thread.sleep(200)
        25
      }
      implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

      // filter out the failed futures
      val fastest = Future.find(Vector(f1, f2, f3)) { _ => true }
      // this doesn't have to be triggered in the test thread so the assertions can fail but the test can still pass
      fastest.onComplete {
        case Success(v) => v shouldBe Some(15)
        case Failure(e) =>
          fail(s"unexpected exception $e")
      }
      // just to verify the result
      Await.result(fastest, 1 seconds)
      // this ensures that the result is correct
      fastest.value shouldBe Some(Success(Some(15)))
    }
  }

  "futures" can {
    "be zipped together" in {
      val f1 = Future.successful("Hello")
      val f2 = Future.successful("World")
      val zipped = f1.zip(f2).map { case (s1, s2) => s"$s1 $s2" }
      awaitAndCheck(zipped, "Hello World")
    }

    "recover from failure" in {
      val DEFAULT_VALUE = 5
      val failedFuture: Future[Int] = Future.failed(new Exception()).recover { case _ => DEFAULT_VALUE }
      awaitAndCheck(failedFuture, DEFAULT_VALUE)
    }

    "be folded" in {
      val futures = Vector(Future.successful(1), Future.successful(2))
      val result = Future.foldLeft[Int, String](futures)("0") { case (acc, x) => acc + x.toString }
      awaitAndCheck(result, "012")
    }
  }

  private def awaitAndCheck(future: Future[_], expectedValue: Any): Any = {
    Await.result(future, 1 second) shouldBe expectedValue
  }
}
