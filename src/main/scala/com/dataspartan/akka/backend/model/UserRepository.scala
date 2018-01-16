package com.dataspartan.akka.backend.model

import java.util.UUID

import akka.actor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.query.QueryProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import slick.jdbc.H2Profile.api._

import scala.concurrent.{Await, ExecutionContext, Future}
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
                      executionContext: ExecutionContext): Future[User] = {
    val query = usersDB.filter(_.userId === userId)
    db.run(query.result.head) map (_.toUser)
  }

  def getUserByLogin(login: String)
                    (implicit db: Database,
                     executionContext: ExecutionContext): Future[User] = {
    val query = usersDB.filter(_.login === login)
    db.run(query.result.head) map (_.toUser)
  }

  def getAddress(addressId: Long)
                (implicit db: Database,
                 executionContext: ExecutionContext): Future[Address] = {
    val query = addressesDB.filter(_.addressId === addressId)
    db.run(query.result.head) map (_.toAddress)
  }

  def createUser(user: User)
                (implicit db: Database,
                 executionContext: ExecutionContext): Future[Long] = {
    val newUser= (usersDB returning  usersDB.map(_.userId)) += UserDBFactory.fromUser(user)
    db.run(newUser)
  }

  def updateUserAddress(userId: Long, address: Address)
                       (implicit db: Database,
                        executionContext: ExecutionContext): Future[ChangeAddressResult] = {
    val newAddress = (addressesDB returning  addressesDB.map(_.addressId)) += AddressDBFactory.fromAddress(address)
    db.run(newAddress).map {
      addressId: Long =>
        val userUpdate = usersDB.filter(_.userId === userId).map(_.addressId).update(Some(addressId))
        return db.run(userUpdate) map (_ => ChangeAddressResult(s"Address updated for User $userId"))
    }
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
