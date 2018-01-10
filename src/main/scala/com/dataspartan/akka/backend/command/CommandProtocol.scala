package com.dataspartan.akka.backend.command

import com.dataspartan.akka.backend.entities.AddressEntities.Address

object CommandProtocol {

  sealed trait Command {
    val commandId: String
  }
  case class ChangeAddress(commandId: String, userId: String, newAddress: Address) extends Command
  case class QuoteInsurance(commandId: String) extends Command
  case class NotifyQuote(commandId: String) extends Command
  case class EndWork(commandId: String) extends Command

  sealed trait CommandAccepted extends Command
  case class ChangeAddressAccepted(commandId: String) extends CommandAccepted

  sealed trait CommandEnd extends Command
  case class ChangeAddressEnd(commandId: String) extends CommandEnd

}