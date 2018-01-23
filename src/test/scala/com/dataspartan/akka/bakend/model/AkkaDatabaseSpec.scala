package com.dataspartan.akka.bakend.model

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import com.dataspartan.akka.backend.model.UserRepository
import com.dataspartan.akka.backend.query.QueryProtocol._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object AkkaDatabaseSpec {
  val addresses = Seq(Address("25", "High Street", "Waltham Forest", "Great London", "E17 7FD", Some(1L)),
    Address("26", "Highgate Street", "Forest", "London", "E17 6FD", Some(2L)))

  val addressTest = Address("26T", "THighgate Street", "TForest", "TLondon", "E17 6TD")

  val users = Seq(User("ljohn", "John", "Doe", Some(1L), addresses.head.addressId),
    User("lfred", "Fred", "Smith", Some(2L), addresses(1).addressId),
    User("lmanu", "Manu", "Perez", Some(3L)),
    User("ljuan", "Juan", "Gonzalez", Some(4L)))

  val userTest = User("login", "name", "surname")

  val insuranceQuotes = Seq(InsuranceQuoteDBFactory.generateQuote(users.head, addresses.head),
    InsuranceQuoteDBFactory.generateQuote(users(1), addresses(1)))
}

class AkkaDatabaseSpec(_system: ActorSystem) extends TestKit(_system)
    with ScalaFutures
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  import AkkaDatabaseSpec._

  def this() = this(ActorSystem("AkkaDatabaseSpec"))
  val db = Database.forConfig("h2mem1")
  val userRepo = system.actorOf(UserRepository.props)
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
  implicit val scalaFuturesConfig: PatienceConfig =  PatienceConfig(timeout=scaled(Span(500, Millis)))

  "UserRepository Actor" should "get the empty user list" in {
      emptyDatabase()
      val future = userRepo ? GetUsers
      future.futureValue shouldBe Seq.empty[User]
  }

  "UserRepository Actor" should "get the user list" in {
    val future = userRepo ? GetUsers
    future.futureValue shouldBe users
  }

  "A UserRepository Actor" should "get user that not exist" in {
    emptyDatabase()
    val future = userRepo ? GetUser(0L)
    future.futureValue shouldBe UserNotFound
  }

  "A UserRepository Actor" should "get user" in {
    val future = userRepo ? GetUser(users.head.userId.get)
    future.futureValue shouldBe users.head
  }

  "A UserRepository Actor" should "get user with database crash" in {
    dropSchemas()
    val future = userRepo ? GetUser(users.head.userId.get)
    future.futureValue shouldBe a [UserQueryFailed]
  }

  "A UserRepository Actor" should "get address that not exist" in {
    emptyDatabase()
    val future = userRepo ? GetAddress(0L)
    future.futureValue shouldBe AddressNotFound
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
    future2.futureValue shouldBe a [NewUserFailed]
  }

  "A UserRepository Actor" should "create address" in {
    val future = userRepo ? NewAddress(comId, addressTest)
    future.futureValue shouldBe a [NewAddressCreated]
    val newAddressResponse: NewAddressCreated = future.futureValue.asInstanceOf[NewAddressCreated]
    val futureAddress = userRepo ? GetAddress(newAddressResponse.addressId)
    futureAddress.futureValue shouldBe addressTest.copy(addressId=Some(newAddressResponse.addressId))
  }

  "A UserRepository Actor" should "create address crash" in {
    dropSchemas()
    val future = userRepo ? NewAddress(comId, addressTest)
    future.futureValue shouldBe a [NewAddressFailed]
  }
}