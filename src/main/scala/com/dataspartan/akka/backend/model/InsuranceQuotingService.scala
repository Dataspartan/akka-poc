package com.dataspartan.akka.backend.model

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.model.UserRepository._
import com.dataspartan.akka.backend.query.QueryProtocol

import scala.concurrent.duration._
import slick.jdbc.H2Profile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Random

object InsuranceQuotingService {
  def props: Props = Props[InsuranceQuotingService]

  def getQuoteByQuoteId(quoteId: Long)
                       (implicit db: Database,
                        executionContext: ExecutionContext): Future[InsuranceQuote] = {
    val query = insuranceQuotesDB.filter(_.quoteId === quoteId)
    db.run(query.result.head) map {
      quote =>
        return getAddress(quote.addressId) map (address => quote.toInsuranceQuote(address))
    }
  }

  private def createQuote(insuranceQuoteF: Future[InsuranceQuote])
                         (implicit db: Database,
                          executionContext: ExecutionContext): Future[Long] = {
    insuranceQuoteF map {
      insuranceQuote =>
        val newQuote= (insuranceQuotesDB returning  insuranceQuotesDB.map(_.quoteId)) +=
          InsuranceQuoteDBFactory.fromInsuranceQuote(insuranceQuote)
        return db.run(newQuote)
    }
  }

  def generateQuote(userId: Long)
                   (implicit db: Database,
                    executionContext: ExecutionContext): Future[InsuranceQuote] = {
      getUserByUserId(userId) map {
        user =>
          return getAddress(user.addressId.get) map {
            address => InsuranceQuote(userId,
                Random.nextDouble(),
                s"Quote for new address $address for the user ${user.surname}, ${user.name}",
                address)
              }
          }
      }
}

class InsuranceQuotingService extends Actor with ActorLogging {
  import QueryProtocol._
  import InsuranceQuotingService._

  implicit val executionContext: ExecutionContext = context.system.dispatcher
  implicit val db: Database = Database.forConfig("h2mem1")

  def receive: Receive = {
    case GetInsuranceQuote(quoteId) =>
      log.info(context.self.toString())
      sender() ! getQuoteByQuoteId(quoteId)
    case QuoteInsurance(userId) =>
      log.info(context.self.toString())
      val nextTick = ThreadLocalRandom.current.nextInt(10, 12).seconds
      Thread.sleep(nextTick.toMillis)
      val quote: Future[InsuranceQuote] = generateQuote(userId)
      val quoteId: Future[Long] = createQuote(quote)
      sender() ! (quoteId map (id => getQuoteByQuoteId(id)))
  }
}