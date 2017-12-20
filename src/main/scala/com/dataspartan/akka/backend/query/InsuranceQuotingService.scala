package com.dataspartan.akka.backend.query

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote

object InsuranceQuotingService {
  def props: Props = Props[InsuranceQuotingService]
}

class InsuranceQuotingService extends Actor with ActorLogging {
  import QueryProtocol._

  def receive: Receive = {
    case GetInsuranceQuote(quoteId) =>
      log.info(context.self.toString())
      sender() ! Option(InsuranceQuote(quoteId, 100, "description",
        Address("number", s"street $quoteId", "town", "county", "postcode")))
  }
}