package com.dataspartan.akka.http

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import akka.stream.ActorMaterializer
import com.dataspartan.akka.backend.comand.master.BackendMasterSingleton
import com.dataspartan.akka.backend.query.{InsuranceQuotingService, UserRepository}
import com.dataspartan.akka.http.routes.{InsuranceManagementRoutes, UserManagementRoutes}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

//#main-class
object HttpRestServer extends App with UserManagementRoutes with InsuranceManagementRoutes {
  implicit val system: ActorSystem = ActorSystem("ClusterSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // Needed for the Future and its methods flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher

  val userRepository: ActorRef = system.actorOf(UserRepository.props)

  val insuranceQuotingService: ActorRef = system.actorOf(InsuranceQuotingService.props)

  val backendMasterProxy: ActorRef = system.actorOf(
      BackendMasterSingleton.proxyProps(system), name = "backendMasterProxy")

  //#main-class
  lazy val routes: Route = RouteConcatenation.concat(userManagementRoutes, insuranceManagementRoutes)
  //#main-class

  println(s"Setted backend timeout: $timeout")

  //#http-server
  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")

  StdIn.readLine()

  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      done.failed.map { ex => log.error(ex, "Failed unbinding") }
      system.terminate()
    }
  //#http-server
  //#main-class
}
//#main-class
