package com.dataspartan.akka.http.routes

import akka.actor.ActorSystem
import akka.event.Logging
import akka.util.Timeout
import com.dataspartan.akka.http.RestJsonSupport

import scala.concurrent.duration._

trait RestRoutes extends RestJsonSupport {

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[RestRoutes])

//  // other dependencies that masterProxy use
//  def masterProxy: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration
}
