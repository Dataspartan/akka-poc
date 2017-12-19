package com.dataspartan.akka.backend.query

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.comand.master.CommandProtocol.UpdateAddress
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult

object UserRepository {
  final case object GetUsers
  final case class GetUser(userId: String)
  final case class GetAddress(userId: String)

  def props: Props = Props[UserRepository]
}

class UserRepository extends Actor with ActorLogging {
  import UserRepository._
  import com.dataspartan.akka.backend.entities.UserEntities._

  var users = Set.empty[User]

  1 to 10 foreach (i => users += User(UUID.randomUUID().toString, s"login$i", s"name$i", s"surname$i"))

  private def getAddress(userId: String): Address = {
    Address("number", s"street $userId", "town", "county", "postcode")
  }

  def receive: Receive = {
    case GetUsers =>
      sender() ! Users(users.toSeq)
    case GetUser(userId) =>
      sender() ! users.find(_.userId == userId)
    case GetAddress(userId) =>
      sender() ! Option(getAddress(userId))
    case UpdateAddress(userId, _) =>
      sender() ! ActionResult(s"Address updated for User $userId")
  }
}
