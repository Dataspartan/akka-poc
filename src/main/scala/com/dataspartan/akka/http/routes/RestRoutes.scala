package com.dataspartan.akka.http.routes

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.util.Timeout
import com.dataspartan.akka.http.RestJsonSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait RestRoutes extends RestJsonSupport {

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[RestRoutes])



  // Required by the `ask` (?) method below
  implicit lazy val timeout: Timeout = system.settings.config.getDuration("http-rest-server.backend-timeout ").getSeconds.seconds
}
