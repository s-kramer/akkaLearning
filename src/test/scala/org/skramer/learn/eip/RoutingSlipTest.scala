package org.skramer.learn.eip

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe
import org.skramer.learn.AkkaLearningTestTrait
import org.skramer.learn.eip.CookieOrderOptions.CookieOrderOptions

case class CookieReceipt(yeast: Option[String] = None, flour: Option[String] = None, sugar: Option[String] = None,
                         milk: Option[Int] = None,
                         oil: Option[Int] = None)

object CookieOrderOptions extends Enumeration {
  type CookieOrderOptions = Value
  val quickCookie, dietCookie = Value
}

class YeastActor extends Actor with RoutingSlipElement {
  override def receive: Receive = {
    case CookieRoutingSlip(steps, msg) => sendToNext(steps, msg.copy(yeast = Some("yeast")))
  }
}

class FlourActor extends Actor with RoutingSlipElement {
  override def receive: Receive = {
    case CookieRoutingSlip(steps, msg) => sendToNext(steps, msg.copy(flour = Some("flour")))
  }
}

class SugarActor extends Actor with RoutingSlipElement {
  override def receive: Receive = {
    case CookieRoutingSlip(steps, msg) => sendToNext(steps, msg.copy(sugar = Some("sugar")))
  }
}

class MilkActor extends Actor with RoutingSlipElement {
  override def receive: Receive = {
    case CookieRoutingSlip(steps, msg) => sendToNext(steps, msg.copy(milk = Some(200)))
  }
}

class OilActor extends Actor with RoutingSlipElement {
  override def receive: Receive = {
    case CookieRoutingSlip(steps, msg) => sendToNext(steps, msg.copy(oil = Some(150)))
  }
}

case class CookieOrder(options: CookieOrderOptions)

case class CookieRoutingSlip(steps: List[ActorRef], msg: CookieReceipt)

trait RoutingSlipElement {
  def sendToNext(steps: List[ActorRef], msg: CookieReceipt): Unit = {
    val next = steps.head
    val newRoutingSlip = steps.tail
    if (newRoutingSlip.isEmpty) {
      next ! msg
    } else {
      next ! CookieRoutingSlip(newRoutingSlip, msg)
    }
  }
}

class CookieMaker(cookieReceiver: ActorRef) extends Actor with RoutingSlipElement {
  private val yeastActor = context.actorOf(Props[YeastActor])
  private val flourActor = context.actorOf(Props[FlourActor])
  private val sugarActor = context.actorOf(Props[SugarActor])
  private val milkActor = context.actorOf(Props[MilkActor])
  private val oilActor = context.actorOf(Props[OilActor])

  override def receive: Receive = {
    case CookieOrder(option) => sendToNext(createRoutingSlip(option), CookieReceipt())
  }

  def createRoutingSlip(options: CookieOrderOptions.CookieOrderOptions): List[ActorRef] = options match {
    case CookieOrderOptions.`quickCookie` => List(flourActor, oilActor, sugarActor, cookieReceiver)
    case CookieOrderOptions.`dietCookie` => List(milkActor, flourActor, oilActor, yeastActor, cookieReceiver)
  }
}

class RoutingSlipTest extends AkkaLearningTestTrait {
  "routing slip" should {
    "specify the simplest path" in {
      val cookieReceiver = TestProbe()
      val cookieMaker = system.actorOf(Props(new CookieMaker(cookieReceiver.ref)))
      cookieMaker ! CookieOrder(CookieOrderOptions.dietCookie)
      cookieReceiver.expectMsg(CookieReceipt(Some("yeast"), Some("flour"), None, Some(200), Some(150)))

      cookieMaker ! CookieOrder(CookieOrderOptions.quickCookie)
      cookieReceiver.expectMsg(CookieReceipt(None, Some("flour"), Some("sugar"), None, Some(150)))
    }
  }
}
