package com.dataspartan.akka.http.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.{get, put}
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete

trait UserManagementRoutes extends RestRoutes {
  import com.dataspartan.akka.http.RestMessage._

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
        //              entity(as[RestMessage.Job]) { job =>
        //                val work = Work(nextWorkId(), job.n)
        //                val jobCreated: Future[Master.Ack] =
        //                  (masterProxy ? work).mapTo[Master.Ack]
        //                onSuccess(jobCreated) { ack =>

        var users: List[User] = List.empty[User]
        1 to 10 foreach (i => users = users :+ User(s"login$i", s"name$i", s"surname$i"))

        val usersResponse = Users(users)
        complete(usersResponse)
        //                }
      }
    )

  def userRoute(userId: String): Route =
    concat(
      get {
        log.info(s"get user info - $userId")
        complete(User(s"login $userId", "name", "surname"))
      }
    )

  def addressRoute(userId: String): Route =
    concat(
      get {
        log.info(s"get address for user - $userId")
        complete(Address("number", s"street $userId", "town", "county", "postcode"))
      },
      put {
        entity(as[Address]) { user =>
          log.info(s"update address for user - $userId")
//          val userCreated: Future[ActionPerformed] =
//            (userRegistryActor ? CreateUser(user)).mapTo[ActionPerformed]
//          onSuccess(userCreated) { performed =>
//            log.info("Created user [{}]: {}", user.name, performed.description)
            complete(StatusCodes.Created, "OK")
//          }
        }
      }
    )
}
