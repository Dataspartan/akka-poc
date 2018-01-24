package com.dataspartan.akka.bakend.model

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import com.dataspartan.akka.backend.model.ModelExceptions._
import com.dataspartan.akka.backend.model.{InsuranceQuotingService, UserRepository}
import com.dataspartan.akka.backend.query.QueryProtocol._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Inside
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.language.postfixOps


object AkkaDatabaseSpec {
  val addresses = Seq(Address("25", "High Street", "Waltham Forest", "Great London", "E17 7FD", Some(1L)),
    Address("26", "Highgate Street", "Forest", "London", "E17 6FD", Some(2L)))

  val addressTest = Address("26T", "THighgate Street", "TForest", "TLondon", "E17 6TD")

  val users = Seq(User("ljohn", "John", "Doe", Some(1L), addresses.head.addressId),
    User("lfred", "Fred", "Smith", Some(2L), addresses(1).addressId),
    User("lmanu", "Manu", "Perez", Some(3L)),
    User("ljuan", "Juan", "Gonzalez", Some(4L)))

  val userTest = User("login", "name", "surname")

  val insuranceQuotes = Seq(InsuranceQuoteDBFactory.generateQuote(users.head, addresses.head).copy(quoteId = Some(1L)),
    InsuranceQuoteDBFactory.generateQuote(users(1), addresses(1)).copy(quoteId = Some(2L)))
}

class AkkaDatabaseSpec(_system: ActorSystem) extends TestKit(_system)
    with ScalaFutures
    with Matchers
    with Inside
    with FlatSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  import AkkaDatabaseSpec._

  def this() = this(ActorSystem("AkkaDatabaseSpec"))
  val db = Database.forConfig("h2mem1")
  val userRepo: ActorRef = system.actorOf(UserRepository.props)
  val insuranceService: ActorRef = system.actorOf(InsuranceQuotingService.props)
  val comId = "comId"

  def createSchemas(): Unit ={
    Await.result(db.run(DBIO.seq(
      // create the schema
      addressesDB.schema.create,
      usersDB.schema.create,
      insuranceQuotesDB.schema.create
    )), Duration.Inf)
  }

  def dropSchemas(): Unit ={
    Await.result(db.run(DBIO.seq(
      insuranceQuotesDB.schema.drop,
      usersDB.schema.drop,
      addressesDB.schema.drop
    )), Duration.Inf)
  }

  def emptyDatabase(): Unit ={
    Try(dropSchemas())
    createSchemas()
  }

  def populateDatabase(): Unit = {
    Await.result(db.run(DBIO.seq(
      addressesDB ++=  addresses map ( a => AddressDBFactory.fromAddress(a)),
      usersDB ++= users map ( u => UserDBFactory.fromUser(u)),
      insuranceQuotesDB ++= insuranceQuotes map (iq => InsuranceQuoteDBFactory.fromInsuranceQuote(iq))
    )), Duration.Inf)
  }

  override def beforeAll(): Unit = {
    createSchemas()
  }

  override def afterAll: Unit = {
    shutdown(system)
  }

  override def beforeEach(): Unit = {
    populateDatabase()
  }

  override def afterEach(): Unit = {
    emptyDatabase()
  }

  implicit def timeout: Timeout = 1 second
  implicit override def patienceConfig: PatienceConfig =  PatienceConfig(timeout=scaled(Span(500, Millis)))

  "UserRepository Actor" should "get the empty user list" in {
      emptyDatabase()
      val future = userRepo ? GetUsers
      future.futureValue shouldBe Seq.empty[User]
  }

  "UserRepository Actor" should "get the user list" in {
    val future = userRepo ? GetUsers
    future.futureValue shouldBe users
  }

  "UserRepository Actor" should "produce an error getting the user list" in {
    dropSchemas()
    val future = userRepo ? GetUsers
    whenReady(future.failed) { ex => ex shouldBe a [DataAccessException] }
  }

  "A UserRepository Actor" should "get user that not exist" in {
    emptyDatabase()
    val future = userRepo ? GetUser(0L)
    whenReady(future.failed) { ex => ex shouldBe a [InstanceNotFoundException] }
  }

  "A UserRepository Actor" should "get user" in {
    val future = userRepo ? GetUser(users.head.userId.get)
    future.futureValue shouldBe users.head
  }

  "A UserRepository Actor" should "get user error" in {
    dropSchemas()
    val future = userRepo ? GetUser(users.head.userId.get)
    whenReady(future.failed) { ex => ex shouldBe a [DataAccessException] }
  }

  "A UserRepository Actor" should "get address that not exist" in {
    emptyDatabase()
    val future = userRepo ? GetAddress(0L)
    whenReady(future.failed) { ex => ex shouldBe a [InstanceNotFoundException] }
  }

  "A UserRepository Actor" should "get address" in {
    val future = userRepo ? GetAddress(addresses.head.addressId.get)
    future.futureValue shouldBe addresses.head
  }

  "A UserRepository Actor" should "create user" in {
    val future = userRepo ? NewUser(comId, userTest)
    future.futureValue shouldBe a [NewUserCreated]
    val newUserResponse: NewUserCreated = future.futureValue.asInstanceOf[NewUserCreated]
    val futureUser = userRepo ? GetUser(newUserResponse.userId)
    futureUser.futureValue shouldBe userTest.copy(userId=Some(newUserResponse.userId))
  }

  "A UserRepository Actor" should "create duplicate user" in {
    val future1 = userRepo ? NewUser(comId, userTest)
    future1.futureValue shouldBe a [NewUserCreated]
    val future2 = userRepo ? NewUser(comId , userTest)
    whenReady(future2.failed) { ex => ex shouldBe a [DataAccessException] }
  }

  "A UserRepository Actor" should "create address" in {
    val future = userRepo ? NewAddress(comId, addressTest)
    future.futureValue shouldBe a [NewAddressCreated]
    val newAddressResponse: NewAddressCreated = future.futureValue.asInstanceOf[NewAddressCreated]
    val futureAddress = userRepo ? GetAddress(newAddressResponse.addressId)
    futureAddress.futureValue shouldBe addressTest.copy(addressId=Some(newAddressResponse.addressId))
  }

  "A UserRepository Actor" should "create address error" in {
    dropSchemas()
    val future = userRepo ? NewAddress(comId, addressTest)
    whenReady(future.failed) { ex => ex shouldBe a [DataAccessException] }
  }

  "A UserRepository Actor" should "change address in user with address" in {
    val userId = users.head.userId
    val userLogin = users.head.login
    val userName = users.head.name
    val userSurname = users.head.surname
    val future = userRepo ? ChangeAddress(comId, userId.get, addressTest)
    future.futureValue shouldBe a [ChangeAddressResult]
    val futureUser = userRepo ? GetUser(userId.get)
    val changedUser: User = futureUser.futureValue.asInstanceOf[User]
    changedUser should matchPattern { case User(`userLogin`, `userName`, `userSurname`, `userId`, Some(_)) => }
    val futureAddress = userRepo ? GetAddress(changedUser.addressId.get)
    futureAddress.futureValue shouldBe addressTest.copy(addressId = changedUser.addressId)
  }

  "A UserRepository Actor" should "change address in user without address" in {
    val userId = users(3).userId
    val userLogin = users(3).login
    val userName = users(3).name
    val userSurname = users(3).surname
    val future = userRepo ? ChangeAddress(comId, userId.get, addressTest)
    future.futureValue shouldBe a [ChangeAddressResult]
    val futureUser = userRepo ? GetUser(userId.get)
    val changedUser: User = futureUser.futureValue.asInstanceOf[User]
    changedUser should matchPattern { case User(`userLogin`, `userName`, `userSurname`, `userId`, Some(_)) => }
    val futureAddress = userRepo ? GetAddress(changedUser.addressId.get)
    futureAddress.futureValue shouldBe addressTest.copy(addressId = changedUser.addressId)
  }

  "A UserRepository Actor" should "change address error" in {
    dropSchemas()
    val userId = users(3).userId
    val future = userRepo ? ChangeAddress(comId, userId.get, addressTest)
    whenReady(future.failed) { ex => ex shouldBe a [DataAccessException] }
  }

  "A InsuranceQuotingService Actor" should "get insurance quote" in {
    val future = insuranceService ? GetInsuranceQuote(insuranceQuotes.head.quoteId.get)
    future.futureValue shouldBe insuranceQuotes.head
  }

  "A InsuranceQuotingService Actor" should "get insurance quote that not exist" in {
    val future = insuranceService ? GetInsuranceQuote(0L)
    whenReady(future.failed) { ex => ex shouldBe a [InstanceNotFoundException] }
  }

  "A InsuranceQuotingService Actor" should "create new quote" in {
    implicit def timeout: Timeout = 15 seconds
    implicit def patienceConfig: PatienceConfig =  PatienceConfig(timeout=scaled(Span(20, Seconds)), interval = scaled(Span(5, Seconds)))
    val userT = users.head
    val addressT = addresses.head
    val future = insuranceService ? QuoteInsurance(comId, userT.userId.get)
    future.futureValue shouldBe a [QuoteInsuranceCreated]
    val quoteCreated: QuoteInsuranceCreated = future.futureValue.asInstanceOf[QuoteInsuranceCreated]
    val futureQuote = insuranceService ? GetInsuranceQuote(quoteCreated.quoteId)
    val expectedResult = InsuranceQuoteDBFactory.generateQuote(userT, addressT).copy(quoteId = Some(quoteCreated.quoteId))

    inside(futureQuote.futureValue) { case InsuranceQuote(userId, quantity, description, address, quoteId)=>
      userId shouldBe expectedResult.userId
      quantity should (be > 0.0 and be <= 1000.0)
      description shouldBe expectedResult.description
      address shouldBe addressT
      quoteId shouldBe expectedResult.quoteId
    }
  }

  "A InsuranceQuotingService Actor" should "create new quote error" in {
    dropSchemas()
    implicit def timeout: Timeout = 15 seconds
    implicit def patienceConfig: PatienceConfig =  PatienceConfig(timeout=scaled(Span(20, Seconds)), interval = scaled(Span(5, Seconds)))
    val userT = users.head
    val future = insuranceService ? QuoteInsurance(comId, userT.userId.get)
    whenReady(future.failed) { ex => ex shouldBe a [DataAccessException] }
  }
}