package com.dataspartan.akka.backend.model

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, Props}
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.model.UserRepository._
import com.dataspartan.akka.backend.query.QueryProtocol

import scala.concurrent.duration._
import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

object InsuranceQuotingService {
  def props: Props = Props[InsuranceQuotingService]

  def getQuoteByQuoteId(quoteId: Long)
                       (implicit db: Database,
                        executionContext: ExecutionContext): Option[InsuranceQuote] = {
    val query = insuranceQuotesDB.filter(_.quoteId === quoteId)
    db.run(query.result.head) map {
      quote =>
        return getAddress(quote.addressId) map (address => quote.toInsuranceQuote(address))
    }

    val qResult = db.run(query.result.head)
    var quote: Option[InsuranceQuote] = None
    qResult onComplete {
      case Success(quoteDb) => {
        val address = getAddress(quoteDb.addressId)
        quote = Some(quoteDb.toInsuranceQuote(address.get))
      }
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    quote
  }

  private def createQuote(insuranceQuote: InsuranceQuote)
                         (implicit db: Database,
                          executionContext: ExecutionContext): Option[Long] = {
    val newQuote = (insuranceQuotesDB returning insuranceQuotesDB.map(_.quoteId)) +=
      InsuranceQuoteDBFactory.fromInsuranceQuote(insuranceQuote)
    val qResult = db.run(newQuote)
    var quoteId: Option[Long] = None
    qResult onComplete {
      case Success(newQuoteId) => quoteId = Some(newQuoteId)
      case Failure(t) => println("An error has occured: " + t.getMessage)
    }
    quoteId
  }

  def generateQuote(userId: Long)
                   (implicit db: Database,
                    executionContext: ExecutionContext): InsuranceQuote = {
    val user = getUserByUserId(userId).get
    val address = getAddress(user.addressId.get).get
    InsuranceQuote(userId,
      Random.nextDouble(),
      s"Quote for new address $address for the user ${user.surname}, ${user.name}",
      address)
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
      val quote: InsuranceQuote = generateQuote(userId)
      sender() ! (createQuote(quote) map (id => getQuoteByQuoteId(id)))
  }
}