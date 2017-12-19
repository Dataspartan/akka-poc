package com.dataspartan.akka.http.routes

//#test-top
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RouteConcatenation
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestActor.AutoPilot
import akka.testkit.{TestActor, TestProbe}
import com.dataspartan.akka.http.HttpRestServer.{insuranceManagementRoutes, userManagementRoutes}
import com.dataspartan.akka.http.RestMessage.{Address, InsuranceQuote, User, Users}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.language.postfixOps

//#set-up
class RestRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
  with UserManagementRoutes with InsuranceManagementRoutes {
  //#test-top

  //  val testProbe = TestProbe()
  //  override val userRegistryActor: ActorRef =
  ////    system.actorOf(UserRegistryActor.props, "userRegistry")
  //    testProbe.ref
  //
  //  val autoPilot = new AutoPilot {
  //    def run(sender: ActorRef, msg: Any): AutoPilot = msg match {
  //      case GetUsers =>
  //        sender ! Users(Seq.empty[User])
  //        TestActor.KeepRunning
  //      case CreateUser(user) =>
  //        sender ! ActionPerformed(s"User ${user.name} created.")
  //        TestActor.KeepRunning
  //      case DeleteUser(name) =>
  //        sender ! ActionPerformed(s"User ${name} deleted.")
  //        TestActor.KeepRunning
  //      case _ =>
  //        TestActor.NoAutoPilot
  //    }
  //  }
  //  testProbe setAutoPilot autoPilot


  lazy val routes = RouteConcatenation.concat(userManagementRoutes, insuranceManagementRoutes)

  //#set-up

  //#actual-test
  "UserManagementRoutes" should {
    "return no users if no present (GET /users)" in {
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = "/users")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[Users] should ===(getUsers(0))
      }
    }
    "return users if present (GET /users)" in {
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = "/users")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[Users] should ===(getUsers(10))
      }
    }
    "return error if user doesn't exist (GET /users/{userId})" in {
      val userId = "userId1"
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/users/$userId")

      request ~> routes ~> check {
        status should ===(StatusCodes.NotFound)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should ===(s"""{"description":"$userId not found"}""")
      }
    }
    "return user information if user exists (GET /users/{userId})" in {
      val userId = "userId1"
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/users/$userId")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[User] should ===(getUser(userId))
      }
    }
    "return error if user doesn't exist (GET /users/{userId}/address)" in {
      val userId = "userId1"
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/users/$userId/address")

      request ~> routes ~> check {
        status should ===(StatusCodes.NotFound)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should ===(s"""{"description":"$userId not found"}""")
      }
    }
    "return user address if user exists (GET /users/{userId}/address)" in {
      val userId = "userId1"
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/users/$userId/address")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[Address] should ===(getAddress(userId))
      }
    }
    "be able to update the address (PUT /users/{userId}/address)" in {
      val userId = "userId1"
      val newAddress = getAddress(userId)
      val addressEntity = Marshal(newAddress).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val request = Put(s"/users/$userId/address").withEntity(addressEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        // we expect the response to be json:
//        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[String] should ===("OK")
      }
    }
  }

  private def getUsers(n: Int): Users = {
    var users: List[User] = List.empty[User]
    if (n > 0) {
      1 to n foreach (i => users = users :+ User(s"login$i", s"name$i", s"surname$i"))
    }
    Users(users)
  }
  private def getUser(userId: String): User = {
    User(s"login $userId", "name", "surname")
  }
  private def getAddress(userId: String): Address = {
    Address("number", s"street $userId", "town", "county", "postcode")
  }

  //#actual-test
  "InsuranceManagementRoutes" should {
    "return error if quoteId doesn't exist (GET /insuranceQuotes/{quoteId})" in {
      val quoteId = 1234
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/insuranceQuotes/$quoteId")

      request ~> routes ~> check {
        status should ===(StatusCodes.NotFound)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should ===(s"""{"description":"$quoteId not found"}""")
      }
    }
    "return quote information if quoteId exists (GET /insuranceQuotes/{quoteId})" in {
      val quoteId = "1234"
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/insuranceQuotes/$quoteId")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[InsuranceQuote] should ===(getInsuranceQuote(quoteId))
      }
    }
  }

  //#actual-test

  private def getInsuranceQuote(quoteId: String): InsuranceQuote = {
    InsuranceQuote(quoteId, 100, "description",
      Address("number", s"street $quoteId", "town", "county", "postcode"))
  }
  //#set-up
}
//#set-up
