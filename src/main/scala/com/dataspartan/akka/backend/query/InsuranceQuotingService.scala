package com.dataspartan.akka.backend.query

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote


object InsuranceQuotingService {
  final case class GetInsuranceQuote(quoteId: String)

  def props: Props = Props[InsuranceQuotingService]
}

class InsuranceQuotingService extends Actor with ActorLogging {
  import InsuranceQuotingService._

  def receive: Receive = {
    case GetInsuranceQuote(quoteId) =>
      sender() ! Option(InsuranceQuote(quoteId, 100, "description",
        Address("number", s"street $quoteId", "town", "county", "postcode")))
  }
}