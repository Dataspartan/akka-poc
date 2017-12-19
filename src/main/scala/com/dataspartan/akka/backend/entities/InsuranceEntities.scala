package com.dataspartan.akka.backend.entities

import com.dataspartan.akka.backend.entities.AddressEntities.Address

object InsuranceEntities {
  final case class InsuranceQuote(quoteId: String, quantity : Double, description: String, address: Address)
}