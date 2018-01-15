package com.dataspartan.akka.backend.model

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.query.QueryProtocol

import scala.concurrent.duration._
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future
import scala.language.postfixOps

object InsuranceQuotingService {
  def props: Props = Props[InsuranceQuotingService]
}

class InsuranceQuotingService extends Actor with ActorLogging {
  import QueryProtocol._

  def receive: Receive = {
    case GetInsuranceQuote(quoteId) =>
      log.info(context.self.toString())
//      sender() ! getQuoteByQuoteId(quoteId)
    case QuoteInsurance() =>
      log.info(context.self.toString())
      val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
      Thread.sleep(nextTick.toMillis)
      val quoteId = 1L
      sender() ! QuoteInsuranceResult(s"Quote Insurance Complete", InsuranceQuote(0L, 100, "description",
        Address("number", s"street $quoteId", "town", "county", "postcode"), Some(quoteId)))
  }


//  def getQuoteByQuoteId(quoteId: Long)(implicit db: Database ): Future[InsuranceQuoteDB] = {
//    val query = insuranceQuotesDB.filter(_.quoteId === quoteId)
//    db.run(query.result.head).map(InsuranceQuoteDBTrans.)
//  }
//
//  def createQuote(insuranceQuote: InsuranceQuote)(implicit db: Database ): Future[Long] = {
//    val newQuote= (insuranceQuotesDB returning  insuranceQuotesDB.map(_.quoteId)) +=
//      InsuranceQuoteDBTrans.fromInsuranceQuote(insuranceQuote)
//    db.run(newQuote)
//  }
}