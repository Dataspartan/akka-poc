package com.dataspartan.akka.bakend.model

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.dataspartan.akka.backend.entities.UserEntities.User
import com.dataspartan.akka.backend.entities.{AddressEntities, InsuranceEntities, UserEntities}
import com.dataspartan.akka.backend.model.UserRepository
import com.dataspartan.akka.backend.query.QueryProtocol._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

class AkkaDatabaseSpec(_system: ActorSystem) extends TestKit(_system)
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("AkkaDatabaseSpec"))

  override def beforeAll(): Unit = {
    val db = Database.forConfig("h2mem1")
    Await.result(db.run(DBIO.seq(
      // create the schema
      AddressEntities.addressesDB.schema.create,
      UserEntities.usersDB.schema.create,
      InsuranceEntities.insuranceQuotesDB.schema.create,
    )), Duration.Inf)
  }

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit def timeout: Timeout = 1 second

  "A UserRepository Actor" should "get the empty user list" in {

    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetUsers
    Await.result(future, 500 millis)
    assert(future.isCompleted && future.value.contains(Success(Seq.empty[User])))
  }

  "A UserRepository Actor" should "get user that not exist" in {

    val userRepo = system.actorOf(UserRepository.props)

    val future = userRepo ? GetUser(0L)
    Await.result(future, 500 millis)
    assert(future.isCompleted && future.value.contains(Success(Option.empty[User])))
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