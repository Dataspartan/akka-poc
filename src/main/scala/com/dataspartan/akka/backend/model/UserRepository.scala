package com.dataspartan.akka.backend.model

import java.util.UUID

import akka.actor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult
import com.dataspartan.akka.backend.query.QueryProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._

object UserRepository {
  def props: Props = Props[UserRepository]
}

class UserRepository extends Actor with ActorLogging {
  import QueryProtocol._
  import com.dataspartan.akka.backend.entities.UserEntities._

  var users = Set.empty[User]

  1 to 10 foreach (i => users += User(UUID.randomUUID().toString, s"login$i", s"name$i", s"surname$i"))

  private def getAddress(userId: String): Address = {
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
      sender() ! ActionResult(s"Address updated for User $userId")
  }
}
