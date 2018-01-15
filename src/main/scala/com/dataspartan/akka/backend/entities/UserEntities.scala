package com.dataspartan.akka.backend.entities

import slick.jdbc.H2Profile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object UserEntities {
  final case class Users(users: Seq[User])
  final case class User(userId: String, login: String, name: String, surname: String)
  final case class UserDB(login: String, name: String, surname: String, userId: Option[Long] = None,
                        addressId: Option[Long] = None)

  final object UserDBTrans {
    def fromUser(user: User) = UserDB(user.login: String, user.name: String, user.surname: String, None, None)
  }

  class UsersDB(tag: Tag) extends Table[UserDB](tag, "USER") {
    // Auto Increment the id primary key column
    def userId = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    // The login can't be null
    def login = column[String]("LOGIN", O.Unique)
    // The name can't be null
    def name = column[String]("NAME")
    // The surname can't be null
    def surname = column[String]("SURNAME")

    def addressId = column[Option[Long]]("ADDRESS_ID")
    def address = foreignKey("ADDRESS_FK", addressId, AddressEntities.addressesDB)(_.addressId.?,
      onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    // the * projection (e.g. select * ...) auto-transforms the tupled
    // column values to / from a User
    def * = (login, name, surname, userId.?, addressId) <> (UserDB.tupled, UserDB.unapply)
  }
  val usersDB = TableQuery[UsersDB]
}

object test extends App {

  val db = Database.forConfig("h2mem1")

  try {
    Await.result(db.run(DBIO.seq(
      // create the schema
      AddressEntities.addressesDB.schema.create,
      UserEntities.usersDB.schema.create,
      InsuranceEntities.insuranceQuotesDB.schema.create,

      // insert two User instances
      UserEntities.usersDB += UserEntities.UserDB("ljohn", "John", "Doe"),
      UserEntities.usersDB += UserEntities.UserDB("lfred", "Fred", "Smith"),
      UserEntities.usersDB += UserEntities.UserDB("lmanu", "Manu", "Perez"),
      UserEntities.usersDB += UserEntities.UserDB("juan", "Juan", "Gonzalez"),

      AddressEntities.addressesDB += AddressEntities.AddressDB("number", "street", "town", "county", "postcode"),
      AddressEntities.addressesDB += AddressEntities.AddressDB("number1", "street1", "town1", "county1", "postcode1"),

//      InsuranceEntities.insuranceQuotesDB += InsuranceEntities.InsuranceQuoteDB(111, 111, 100, "description"),
//      InsuranceEntities.insuranceQuotesDB += InsuranceEntities.InsuranceQuoteDB(112, 112, 101, "description1")

    )), Duration.Inf)

    import scala.concurrent.ExecutionContext.Implicits.global



    println("hola ")

//    result foreach { row =>
//      println("user whose username is 'john' has id "+row._1 )
//    }

//    val action1 = sql"select * from USER".as[UserEntities.UsersDB]
//    print(db.run(action1))
//
//    Await.result(result, 1 second)
//    result.map(print(_))


    val newUser = ( UserEntities.usersDB returning  UserEntities.usersDB.map(_.userId)) +=  UserEntities.UserDB("juan", "Juan", "Gonzalez")

    var userId= 0L
    // note that the newly-added id is returned instead of
    // the number of affected rows
    Await.result(db.run(newUser).map { newId =>
      newId match {
        case x:Long => println(s"last entry added had id $x"); userId = x
      }
    }.recover {
      case e:  java.sql.SQLException => println("Caught exception: " + e.getMessage)
    }, 1 second)



    var id = 0L
    val newAddress = ( AddressEntities.addressesDB returning  AddressEntities.addressesDB.map(_.addressId)) +=
      AddressEntities.AddressDB("number", "street", "town", "county", "postcode")

    // note that the newly-added id is returned instead of
    // the number of affected rows
    Await.result(db.run(newAddress).map { newId =>
      newId match {
        case x:Long => println(s"last entry added had id $x"); id = x

      }
    }.recover {
      case e:  java.sql.SQLException => println("Caught exception: " + e.getMessage)
    }, 1 second)

    // update users u set u.name='peter' where u.id=1
    val qu = UserEntities.usersDB.filter(_.userId === userId).map(_.addressId).update(Some(id))

    Await.result(
      db.run(qu).map { numAffectedRows =>
        println(numAffectedRows)
      }, 1 second)


    val q = UserEntities.usersDB.sortBy(_.name.desc.nullsFirst)
    val result: Future[Seq[UserEntities.UserDB]] = db.run(q.result)

    Await.result(
      result.map { res =>
        println(s"The newly-added user had the following id $res")
      }.recover {
        case e: java.sql.SQLException => println("Caught exception in the for block: " + e.getMessage)
      }, 1 second)

  }
}