package com.dataspartan.akka.backend.comand.master

import com.dataspartan.akka.backend.entities.AddressEntities.Address

object CommandProtocol {
  final case class UpdateAddress(userId: String, address: Address)
}