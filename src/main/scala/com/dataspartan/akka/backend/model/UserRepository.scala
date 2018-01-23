package com.dataspartan.akka.backend.model

import java.util.{NoSuchElementException, UUID}

import akka.actor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import slick.jdbc.H2Profile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import com.dataspartan.akka.backend.query.QueryProtocol._

import scala.concurrent.duration._

object UserRepository {
  def props: Props = Props[UserRepository]
}

class UserRepository extends Actor with ActorLogging {
  import slick.lifted.Query

  implicit val executionContext: ExecutionContext = context.system.dispatcher
  implicit val db: Database = Database.forConfig("h2mem1")
  implicit def timeout: Timeout = 500 millis

  override def preStart(): Unit = {
    log.info(s"Starting ${context.self.path}")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit =  {
    log.info(s"Restarting ${context.self.path}")
  }

  def receive: Receive = {
    case GetUsers =>
      log.info(context.self.toString())
      getUsers(sender)
    case GetUser(userId) =>
      log.info(context.self.toString())
      getUserByUserId(sender, userId)
    case NewUser(commandId, user) =>
      createUser(sender, commandId, user)
    case NewAddress(commandId, address) =>
      createAddress(sender, commandId, address)
    case GetAddress(addressId) =>
      log.info(context.self.toString())
      getAddress(sender, addressId)
    case ChangeAddress(commandId, userId, newAddress) =>
      log.info(context.self.toString())
      updateUserAddress(sender, commandId, userId, newAddress)
  }

  private def getUsers(sender: ActorRef): Unit = {
    val qResult = db.run(usersDB.result) map (_ map(_.toUser))

    qResult onComplete {
      case Success(users) => sender ! users
      case Failure(ex) => sender ! UserQueryFailed(ex)
    }
  }

  private def getUserByUserId(sender: ActorRef, userId: Long): Unit = {
    val query = usersDB.filter(_.userId === userId)
    getUser(sender, query)
  }

  private def getUserByLogin(sender: ActorRef, login: String): Unit = {
    val query: Query[UsersDB, UserDB, Seq] = usersDB.filter(_.login === login)
    getUser(sender, query)
  }

  private def getUser(sender: ActorRef, query: Query[UsersDB, UserDB, Seq]): Unit = {
    val qResult = db.run(query.result.head) map (_.toUser)
    qResult onComplete {
      case Success(user) => sender ! user
      case Failure(ex) => ex match {
        case _: NoSuchElementException => sender ! UserNotFound
        case _ => sender ! UserQueryFailed(ex)
      }
    }
  }

  private def getAddress(sender: ActorRef, addressId: Long): Unit = {
    val query = addressesDB.filter(_.addressId === addressId)
    val qResult = db.run(query.result.head) map (_.toAddress)
    qResult onComplete {
      case Success(address) => sender ! address
      case Failure(ex) => ex match {
        case _: NoSuchElementException => sender ! AddressNotFound
        case _ => sender ! UserQueryFailed(ex)
      }
    }
  }

  private def createUser(sender: ActorRef, commandId: String, user: User): Unit = {
    val newUser= (usersDB returning  usersDB.map(_.userId)) += UserDBFactory.fromUser(user)
    val qResult =  db.run(newUser)

    qResult onComplete {
      case Success(newUserId) => sender ! NewUserCreated(commandId, newUserId)
      case Failure(ex) => sender ! NewUserFailed(commandId, ex)
    }
  }

  private def createAddress(sender: ActorRef, commandId: String, address: Address): Unit = {
    val newAddress = (addressesDB returning  addressesDB.map(_.addressId)) += AddressDBFactory.fromAddress(address)
    val qResult =  db.run(newAddress)
    qResult onComplete {
      case Success(newAddressId) => sender ! NewAddressCreated(commandId, newAddressId)
      case Failure(ex) => sender ! NewAddressFailed(commandId, ex)
    }
  }

  private def updateUserAddress(sender: ActorRef, commandId: String, userId: Long, address: Address): Unit = {
    val addressIdResp: Future[Any] = self ? NewAddress(commandId, address)

    addressIdResp onComplete {
      case Success(NewAddressCreated(_, addressId)) => {
        val userUpdate = usersDB.filter(_.userId === userId).map(_.addressId).update(Some(addressId))
        val qResult = db.run(userUpdate)
        qResult onComplete {
          case Success(numRows) => sender ! ChangeAddressResult(s"Address updated for User $userId")
          case Failure(ex) => sender ! ChangeAddressFailed(commandId, ex)
        }
      }
      case Success(failure) => sender ! failure
      case Failure(ex) => sender ! ChangeAddressFailed(commandId, ex)
    }
  }
}
