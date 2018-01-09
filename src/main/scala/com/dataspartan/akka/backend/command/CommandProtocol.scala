package com.dataspartan.akka.backend.command

import com.dataspartan.akka.backend.entities.AddressEntities.Address

object CommandProtocol {

  sealed trait Command
  case class ChangeAddress(commandId: String, userId: String, newAddress: Address) extends Command
  case object QuoteInsurance extends Command
  case object NotifyQuote extends Command
  case object EndWork extends Command
}