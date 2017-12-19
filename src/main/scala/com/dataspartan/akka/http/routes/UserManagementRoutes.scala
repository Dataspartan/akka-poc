package com.dataspartan.akka.http.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.{get, put}
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.dataspartan.akka.backend.entities.UserEntities.{User, Users}
import com.dataspartan.akka.backend.query.UserRepository.{GetAddress, GetUser, GetUsers}
import akka.pattern.ask
import com.dataspartan.akka.backend.comand.master.CommandProtocol.UpdateAddress
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult

import scala.concurrent.Future

trait UserManagementRoutes extends RestRoutes {

  def userRepository: ActorRef

  def backendMasterProxy: ActorRef

  //#all-routes
  lazy val userManagementRoutes: Route =
    pathPrefix("users") {
      concat(
        pathEnd(usersRoute()),
        path(Segment) { userId => userRoute(userId) },
        path(Segment / "address") {userId => addressRoute(userId)
        }
      )
    }
  //#all-routes

  def usersRoute(): Route =
    concat(
      get {
        log.info("get All users")
        val users: Future[Users] =
          (userRepository ? GetUsers).mapTo[Users]
        complete(users)
      }
    )

  def userRoute(userId: String): Route =
    concat(
      get {
        log.info(s"get user info - $userId")
        val maybeUser: Future[Option[User]] =
          (userRepository ? GetUser(userId)).mapTo[Option[User]]
        rejectEmptyResponse {
          complete(maybeUser)
        }
      }
    )

  def addressRoute(userId: String): Route =
    concat(
      get {
        log.info(s"get address for user - $userId")
        val maybeAddress: Future[Option[Address]] =
          (userRepository ? GetAddress(userId)).mapTo[Option[Address]]
        rejectEmptyResponse {
          complete(maybeAddress)
        }
      },
      put {
        // TODO: It is a command, send to backendMasterProxy
        entity(as[Address]) { address =>
          log.info(s"update address for user - $userId")
          val addressUpdated: Future[ActionResult] =
            (backendMasterProxy ? UpdateAddress(userId, address)).mapTo[ActionResult]
          onSuccess(addressUpdated) { result =>
            log.info(s"Address updated user [$userId]: ${result.description}")
            complete(StatusCodes.Created, result)
          }
        }
      }
    )
}
