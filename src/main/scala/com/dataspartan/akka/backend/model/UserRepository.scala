package com.dataspartan.akka.backend.model

import java.util.UUID

import akka.actor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.query.QueryProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import slick.jdbc.H2Profile.api._
import slick.lifted.Query

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Failure, Success}

object UserRepository {
  def props: Props = Props[UserRepository]

  def getUsers()
              (implicit db: Database,
               executionContext: ExecutionContext): Seq[User] = {
    val qResult = db.run(usersDB.result) map (_ map(_.toUser))

    var users = Seq.empty[User]
    qResult onComplete {
      case Success(userList) => users = userList
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    users
  }

  def getUserByUserId(userId: Long)
                     (implicit db: Database,
                      executionContext: ExecutionContext): Option[User] = {
    val query = usersDB.filter(_.userId === userId)
    getUser(query)
  }

  def getUserByLogin(login: String)
                    (implicit db: Database,
                     executionContext: ExecutionContext): Option[User] = {
    val query: Query[UsersDB, UserDB, Seq] = usersDB.filter(_.login === login)
    getUser(query)
  }

  private def getUser(query: Query[UsersDB, UserDB, Seq])
                    (implicit db: Database,
                     executionContext: ExecutionContext): Option[User] = {
    val qResult = db.run(query.result.head) map (_.toUser)
    var user: Option[User] = None
    qResult onComplete {
      case Success(userDb) => user = Some(userDb)
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    user
  }

  def getAddress(addressId: Long)
                (implicit db: Database,
                 executionContext: ExecutionContext): Option[Address] = {
    val query = addressesDB.filter(_.addressId === addressId)
    val qResult = db.run(query.result.head) map (_.toAddress)
    var address: Option[Address] = None
    qResult onComplete {
      case Success(addressDb) => address = Some(addressDb)
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    address
  }

  def createUser(user: User)
                (implicit db: Database,
                 executionContext: ExecutionContext): Option[Long] = {
    val newUser= (usersDB returning  usersDB.map(_.userId)) += UserDBFactory.fromUser(user)
    val qResult =  db.run(newUser)
    var userId: Option[Long] = None
    qResult onComplete {
      case Success(newUserId) => userId = Some(newUserId)
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    userId
  }

  def createAddress(address: Address)
                (implicit db: Database,
                 executionContext: ExecutionContext): Option[Long] = {
    val newAddress = (addressesDB returning  addressesDB.map(_.addressId)) += AddressDBFactory.fromAddress(address)
    val qResult =  db.run(newAddress)
    var addressId: Option[Long] = None
    qResult onComplete {
      case Success(newAddressId) => addressId = Some(newAddressId)
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    addressId
  }


  def updateUserAddress(userId: Long, address: Address)
                       (implicit db: Database,
                        executionContext: ExecutionContext): ChangeAddressResult = {
    val addressId = createAddress(address)
    val userUpdate = usersDB.filter(_.userId === userId).map(_.addressId).update(addressId)
    val qResult = db.run(userUpdate)

    var res = ChangeAddressResult(s"Error")
    qResult onComplete {
      case Success(numRows) => res = ChangeAddressResult(s"Address updated for User $userId")
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    res
  }
}

class UserRepository extends Actor with ActorLogging {
  import QueryProtocol._
  import UserRepository._

  implicit val executionContext: ExecutionContext = context.system.dispatcher
  implicit val db: Database = Database.forConfig("h2mem1")

  override def preStart(): Unit = {
    log.info(s"Starting ${context.self.path}")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit =  {
    log.info(s"Restarting ${context.self.path}")
  }

  def receive: Receive = {
    case GetUsers =>
      log.info(context.self.toString())
      sender() ! getUsers()
    case GetUser(userId) =>
      log.info(context.self.toString())
      sender() ! getUserByUserId(userId)
    case GetAddress(addressId) =>
      log.info(context.self.toString())
      sender() ! getAddress(addressId)
    case ChangeAddress(_, userId, newAddress) =>
      log.info(context.self.toString())
      sender() ! updateUserAddress(userId, newAddress)
  }
}
