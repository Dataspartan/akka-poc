package com.dataspartan.akka.http.routes

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.dataspartan.akka.http.RestJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._

trait RestRoutes extends RestJsonSupport {

  // we leave these abstract, since they will be provided by the App
  protected implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[RestRoutes])

  def queryMasterProxy: ActorRef

  def commandMasterProxy: ActorRef

  // Required by the `ask` (?) method below
  implicit def timeout: Timeout

  val routeExceptionHandler = ExceptionHandler {
    case _: AskTimeoutException => {
      extractUri { uri =>
        log.error(s"Request to $uri could not be handled normally")
        complete(HttpResponse(RequestTimeout, entity = "Request Timeout"))
      }
    }
  }
}
