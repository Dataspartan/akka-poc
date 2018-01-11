package com.dataspartan.akka.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.dataspartan.akka.backend.command.master.CommandMasterSingleton
import com.dataspartan.akka.backend.query.master.QueryMasterSingleton
import com.dataspartan.akka.http.routes.{InsuranceManagementRoutes, UserManagementRoutes}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object HttpRestServer extends UserManagementRoutes with InsuranceManagementRoutes {

  protected implicit val system: ActorSystem = ActorSystem("ClusterSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // Needed for the Future and its methods flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher

  val queryMasterProxy: ActorRef = system.actorOf(
    QueryMasterSingleton.proxyProps(system), name = "queryMasterProxy")

  val commandMasterProxy: ActorRef = system.actorOf(
    CommandMasterSingleton.proxyProps(system), name = "commandMasterProxy")

  lazy val routes: Route = RouteConcatenation.concat(userManagementRoutes, insuranceManagementRoutes)

  lazy val host: String = system.settings.config.getString("http-rest-server.http-host")
  lazy val port: Int = system.settings.config.getInt("http-rest-server.http-port")
  lazy val timeout: Timeout = system.settings.config.getDuration("http-rest-server.timeout").getSeconds.seconds

  def run():Unit = {

    val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(routes, host, port)

    println(s"Server online at http://$host:$port/")
    println(s"Setted backend timeout: $timeout")
    println(s"Press RETURN to stop...")

    StdIn.readLine()

    serverBindingFuture
      .flatMap(_.unbind())
      .onComplete { done =>
        done.failed.map { ex => log.error(ex, "Failed unbinding") }
        system.terminate()
      }
  }
}
