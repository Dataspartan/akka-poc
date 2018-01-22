package com.dataspartan.akka.bakend.model

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol.NewUser
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import com.dataspartan.akka.backend.model.UserRepository
import com.dataspartan.akka.backend.query.QueryProtocol._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Try}
import scala.language.postfixOps

object AkkaDatabaseSpec {
  val addresses = Seq(Address("25", "High Street", "Waltham Forest", "Great London", "E17 7FD", Some(1L)),
    Address("26", "Highgate Street", "Forest", "London", "E17 6FD", Some(2L)))

  val users = Seq(User("ljohn", "John", "Doe", Some(1L), addresses.head.addressId),
    User("lfred", "Fred", "Smith", Some(2L), addresses(1).addressId),
    User("lmanu", "Manu", "Perez", Some(3L)),
    User("ljuan", "Juan", "Gonzalez", Some(4L)))

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


  "UserRepository Actor" should "get the empty user list" in {
      emptyDatabase()
      val userRepo = system.actorOf(UserRepository.props)

      val future = userRepo ? GetUsers
      Await.result(future, timeout.duration)
      assert(future.isCompleted && future.value.contains(Success(Seq.empty[User])))
  }


  "UserRepository Actor" should "get the user list" in {
    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetUsers
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.contains(Success(users)))
  }


  "A UserRepository Actor" should "get user that not exist" in {
    emptyDatabase()
    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetUser(0L)
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.contains(Success(UserNotFound)))
  }

  "A UserRepository Actor" should "get user" in {
    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetUser(users.head.userId.get)
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.contains(Success(users.head)))
  }

  "A UserRepository Actor" should "get user with database crash" in {
    dropSchemas()
    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetUser(users.head.userId.get)
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.exists(_ match {case Success(r) => r.isInstanceOf[UserQueryFailed]}))
  }

  "A UserRepository Actor" should "get address that not exist" in {
    emptyDatabase()
    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetAddress(0L)
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.contains(Success(AddressNotFound)))
  }

  "A UserRepository Actor" should "get address" in {
    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetAddress(addresses.head.addressId.get)
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.contains(Success(addresses.head)))
  }

  "A UserRepository Actor" should "create user" in {
    val userRepo = system.actorOf(UserRepository.props)
    val user = User("login", "name", "surname")
    val future = userRepo ? NewUser("comId" , user)
    Await.result(future, timeout.duration)
    assert(future.isCompleted && future.value.exists(_ match {case Success(r) => r.isInstanceOf[Long]}))
  }
}



//object test {
//
//
//
//  try {
//    Await.result(db.run(DBIO.seq(
//      // create the schema
//      AddressEntities.addressesDB.schema.create,
//      UserEntities.usersDB.schema.create,
//      InsuranceEntities.insuranceQuotesDB.schema.create,
//
//      // insert two User instances
//      UserEntities.usersDB += UserEntities.UserDB("ljohn", "John", "Doe"),
//      UserEntities.usersDB += UserEntities.UserDB("lfred", "Fred", "Smith"),
//      UserEntities.usersDB += UserEntities.UserDB("lmanu", "Manu", "Perez"),
//      UserEntities.usersDB += UserEntities.UserDB("juan", "Juan", "Gonzalez"),
//
//      AddressEntities.addressesDB += AddressEntities.AddressDB("number", "street", "town", "county", "postcode"),
//      AddressEntities.addressesDB += AddressEntities.AddressDB("number1", "street1", "town1", "county1", "postcode1"),
//
//      //      InsuranceEntities.insuranceQuotesDB += InsuranceEntities.InsuranceQuoteDB(111, 111, 100, "description"),
//      //      InsuranceEntities.insuranceQuotesDB += InsuranceEntities.InsuranceQuoteDB(112, 112, 101, "description1")
//
//    )), Duration.Inf)
//
//
//
//    println("hola ")
//
//    //    result foreach { row =>
//    //      println("user whose username is 'john' has id "+row._1 )
//    //    }
//
//    //    val action1 = sql"select * from USER".as[UserEntities.UsersDB]
//    //    print(db.run(action1))
//    //
//    //    Await.result(result, 1 second)
//    //    result.map(print(_))
//
//
//    val newUser = ( UserEntities.usersDB returning  UserEntities.usersDB.map(_.userId)) +=  UserEntities.UserDB("juan", "Juan", "Gonzalez")
//
//    var userId= 0L
//    // note that the newly-added id is returned instead of
//    // the number of affected rows
//    Await.result(db.run(newUser).map { newId =>
//      newId match {
//        case x:Long => println(s"last entry added had id $x"); userId = x
//      }
//    }.recover {
//      case e:  java.sql.SQLException => println("Caught exception: " + e.getMessage)
//    }, 1 second)
//
//
//
//    var id = 0L
//    val newAddress = ( AddressEntities.addressesDB returning  AddressEntities.addressesDB.map(_.addressId)) +=
//      AddressEntities.AddressDB("number", "street", "town", "county", "postcode")
//
//    // note that the newly-added id is returned instead of
//    // the number of affected rows
//    Await.result(db.run(newAddress).map { newId =>
//      newId match {
//        case x:Long => println(s"last entry added had id $x"); id = x
//
//      }
//    }.recover {
//      case e:  java.sql.SQLException => println("Caught exception: " + e.getMessage)
//    }, 1 second)
//
//    // update users u set u.name='peter' where u.id=1
//    val qu = UserEntities.usersDB.filter(_.userId === userId).map(_.addressId).update(Some(id))
//
//    Await.result(
//      db.run(qu).map { numAffectedRows =>
//        println(numAffectedRows)
//      }, 1 second)
//
//
//    val q = UserEntities.usersDB.sortBy(_.name.desc.nullsFirst)
//    val result: Future[Seq[UserEntities.UserDB]] = db.run(q.result)
//
//    Await.result(
//      result.map { res =>
//        println(s"The newly-added user had the following id $res")
//      }.recover {
//        case e: java.sql.SQLException => println("Caught exception in the for block: " + e.getMessage)
//      }, 1 second)
//
//  }
//}