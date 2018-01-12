package com.dataspartan.akka.backend.model

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote
import com.dataspartan.akka.backend.query.QueryProtocol
import scala.concurrent.duration._

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
    case QuoteInsurance() =>
      log.info(context.self.toString())
      val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
      Thread.sleep(nextTick.toMillis)
      val quoteId = "qid"
      sender() ! QuoteInsuranceResult(s"Quote Insurance Complete", InsuranceQuote(quoteId, 100, "description",
        Address("number", s"street $quoteId", "town", "county", "postcode")))


  }
}