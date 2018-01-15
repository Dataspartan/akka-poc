package com.dataspartan.akka.backend.model

import java.util.UUID

import akka.actor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.query.QueryProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.UserEntities._
import slick.jdbc.H2Profile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object UserRepository {
  def props: Props = Props[UserRepository]
}

class UserRepository extends Actor with ActorLogging {
  import QueryProtocol._

  implicit val executionContext: ExecutionContext = context.system.dispatcher

  var users = Set.empty[User]

  1 to 10 foreach (i => users += User(UUID.randomUUID().toString, s"login$i", s"name$i", s"surname$i"))

  private def getAddress(userId: Long): Address = {
    Address("number", s"street $userId", "town", "county", "postcode")
  }

  override def preStart(): Unit = {
    log.info(s"Starting ${context.self.path}")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit =  {
    log.info(s"Restarting ${context.self.path}")
  }

  def receive: Receive = {
    case GetUsers =>
      log.info(context.self.toString())
      sender() ! Users(users.toSeq)
    case GetUser(userId) =>
      log.info(context.self.toString())
      sender() ! users.find(_.userId == userId)
    case GetAddress(userId) =>
      log.info(context.self.toString())
      sender() ! Option(getAddress(userId))
    case ChangeAddress(_, userId, newAddress) =>
      log.info(context.self.toString())
      sender() ! ChangeAddressResult(s"Address updated for User $userId")
  }

  def getUsers()(implicit db: Database): Future[Seq[UserDB]] = {
    db.run(usersDB.result)
  }

  def getUserByUserId(userId: Long)(implicit db: Database ): Future[UserDB] = {
    val query = usersDB.filter(_.userId === userId)
    db.run(query.result.head)
  }

  def getUserByLogin(login: String)(implicit db: Database ): Future[UserDB] = {
    val query = usersDB.filter(_.login === login)
    db.run(query.result.head)
  }

  def createUser(user: User)(implicit db: Database ): Future[Long] = {
    val newUser= (usersDB returning  usersDB.map(_.userId)) += UserDBTrans.fromUser(user)
    db.run(newUser)
  }

  def updateUserAddress(userId: Long, address: Address)(implicit db: Database ): Future[Int] = {
    val newAddress = (addressesDB returning  addressesDB.map(_.addressId)) += AddressDBTrans.fromAddress(address)
    db.run(newAddress).map {
      addressId: Long =>
        val userUpdate = usersDB.filter(_.userId === userId).map(_.addressId).update(Some(addressId))
        return db.run(userUpdate)
    }
  }
}
