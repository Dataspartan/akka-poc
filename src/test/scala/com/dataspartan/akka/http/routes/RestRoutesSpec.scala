package com.dataspartan.akka.http.routes

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RouteConcatenation
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestActor.AutoPilot
import akka.testkit.{TestActor, TestProbe}
import akka.util.Timeout
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote
import com.dataspartan.akka.backend.entities.UserEntities.{User, Users}
import com.dataspartan.akka.backend.query.QueryProtocol.GetInsuranceQuote
import com.dataspartan.akka.backend.query.QueryProtocol.{GetAddress, GetUser, GetUsers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.language.postfixOps

class RestRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
  with UserManagementRoutes with InsuranceManagementRoutes {

  val testProbe = TestProbe()
  override val queryMasterProxy: ActorRef = testProbe.ref
  override val commandMasterProxy: ActorRef = testProbe.ref
  override val timeout: Timeout = 5 seconds

  private def getUsers(n: Int): Users = {
    var users: List[User] = List.empty[User]
//    if (n > 0) {
//      1 to n foreach (i => users = users :+ User("f0a2d772-23e1-4af8-9b33-9d1e26b3cd13", s"login$i", s"name$i", s"surname$i"))
//    }
    Users(users)
  }
  private def getUser(userId: Long): User = {
    User(s"login $userId", "name", "surname")
  }
  private def getAddress(userId: Long): Address = {
    Address("number", s"street $userId", "town", "county", "postcode")
  }

  private def getInsuranceQuote(quoteId: Long): InsuranceQuote = {
    InsuranceQuote(0L, 100, "description",
      Address("number", s"street $quoteId", "town", "county", "postcode"), Some(quoteId))
  }

  val autoPilot = new AutoPilot {
    def run(sender: ActorRef, msg: Any): AutoPilot = msg match {
      case GetUsers =>
        sender ! getUsers(10)
        TestActor.KeepRunning
      case GetUser(userId) =>
        sender ! Option(getUser(userId))
        TestActor.KeepRunning
      case GetAddress(userId) =>
        sender ! Option(getAddress(userId))
        TestActor.KeepRunning
      case GetInsuranceQuote(quoteId) =>
        sender ! Option(getInsuranceQuote(quoteId))
        TestActor.KeepRunning
      case ChangeAddress(commandId, userId, _) =>
        sender ! ChangeAddressResult(s"Address updated for User $userId")
        TestActor.KeepRunning
      case _ =>
        TestActor.NoAutoPilot
    }
  }
  testProbe setAutoPilot autoPilot

  lazy val routes = RouteConcatenation.concat(userManagementRoutes, insuranceManagementRoutes)

  "UserManagementRoutes" should {
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
    "return user information if user exists (GET /users/{userId})" in {
      val userId = 1L
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/users/$userId")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        entityAs[User] should ===(getUser(userId))
      }
    }
    "return user address if user exists (GET /users/{userId}/address)" in {
      val userId = 1L
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
      val userId = 1L
      val newAddress = getAddress(userId)
      val addressEntity = Marshal(newAddress).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val request = Put(s"/users/$userId/address").withEntity(addressEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[ChangeAddressResult] should ===(ChangeAddressResult(s"Address updated for User $userId"))
      }
    }
  }

  //#actual-test
  "InsuranceManagementRoutes" should {
    "return quote information if quoteId exists (GET /insuranceQuotes/{quoteId})" in {
      val quoteId = 1234L
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = s"/insuranceQuotes/$quoteId")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[InsuranceQuote] should ===(getInsuranceQuote(quoteId))
      }
    }
  }
}

