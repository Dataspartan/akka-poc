package com.dataspartan.akka.http.routes

import akka.http.scaladsl.model.{DateTime, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.{get, put}
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.dataspartan.akka.backend.entities.UserEntities.{User, Users}
import com.dataspartan.akka.backend.query.QueryProtocol.{GetAddress, GetUser, GetUsers}
import akka.pattern.ask
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities.Address

import scala.concurrent.Future

trait UserManagementRoutes extends RestRoutes {


  //#all-routes
  lazy val userManagementRoutes: Route = handleExceptions(routeExceptionHandler) {
    pathPrefix("users") {
      concat(
        pathEnd(usersRoute()),
        path(LongNumber) { userId => userRoute(userId) },
        path(LongNumber / "address") { userId => addressRoute(userId)
        }
      )
    }
  }
  //#all-routes

  def usersRoute(): Route =
    concat(
      get {
        log.info("get All users")
        val users: Future[Users] =
          (queryMasterProxy ? GetUsers).mapTo[Users]
        complete(users)
      }
    )

  def userRoute(userId: Long): Route =
    concat(
      get {
        log.info(s"get user info - $userId")
        val maybeUser: Future[Option[User]] =
          (queryMasterProxy ? GetUser(userId)).mapTo[Option[User]]
        rejectEmptyResponse {
          complete(maybeUser)
        }
      }
    )

  def addressRoute(userId: Long): Route =
    concat(
      get {
        log.info(s"get address for user - $userId")
        val maybeAddress: Future[Option[Address]] =
          (queryMasterProxy ? GetAddress(userId)).mapTo[Option[Address]]
        rejectEmptyResponse {
          complete(maybeAddress)
        }
      },
      put {
        // TODO: It is a command, send to backendMasterProxy
        entity(as[Address]) { address =>
          val commandId = s"${userId}_${DateTime.now.toIsoDateTimeString()}"
          log.info(s"update address for user - $userId - with commandId '$commandId'")
          val addressUpdated: Future[ChangeAddressResult] =
            (commandMasterProxy ? ChangeAddress(commandId, userId, address)).mapTo[ChangeAddressResult]
          onSuccess(addressUpdated) { result =>
            log.info(s"Address updated user [$userId]: ${result.description}")
            complete(StatusCodes.Created, result)
          }
        }
      }
    )
}
