package com.dataspartan.akka.http.routes

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.util.Timeout
import com.dataspartan.akka.http.RestJsonSupport

trait RestRoutes extends RestJsonSupport {

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[RestRoutes])

  def queryMasterProxy: ActorRef

  def commandMasterProxy: ActorRef

  // Required by the `ask` (?) method below
  implicit def timeout: Timeout
}
