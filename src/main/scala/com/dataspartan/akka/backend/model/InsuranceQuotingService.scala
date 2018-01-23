package com.dataspartan.akka.backend.model

import java.util.NoSuchElementException
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.entities.InsuranceEntities._
import com.dataspartan.akka.backend.query.QueryProtocol

import scala.concurrent.duration._
import slick.jdbc.H2Profile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.UserEntities.User

object InsuranceQuotingService {
  def props: Props = Props[InsuranceQuotingService]
}

class InsuranceQuotingService extends Actor with ActorLogging {
  import QueryProtocol._

  implicit val executionContext: ExecutionContext = context.system.dispatcher
  implicit val db: Database = Database.forConfig("h2mem1")
  implicit def timeout: Timeout = 500 millis

  val userRepo: ActorRef = context.actorOf(UserRepository.props, "userRepo")

  def receive: Receive = {
    case GetInsuranceQuote(quoteId) =>
      log.info(context.self.toString())
      getQuoteByQuoteId(sender, quoteId)
    case QuoteInsurance(commandId, userId) =>
      log.info(context.self.toString())
      val nextTick = ThreadLocalRandom.current.nextInt(10, 12).seconds
      Thread.sleep(nextTick.toMillis)
      createQuote(sender, commandId, userId)
  }

  private def getQuoteByQuoteId(sender: ActorRef, quoteId: Long): Unit = {
    val query = insuranceQuotesDB.filter(_.quoteId === quoteId)
    val qResult = db.run(query.result.head)
    qResult onComplete {
      case Success(quote) => {
        val addressResp: Future[Address] = (userRepo ? GetAddress(quote.addressId)).mapTo[Address]
        addressResp onComplete {
          case Success(address) => sender ! quote.toInsuranceQuote(address)
          case Failure(ex) => sender ! InsuranceQueryFailed(ex)
        }
      }
      case Failure(ex) => ex match {
          case _: NoSuchElementException => sender ! InsuranceQuoteNotFound
          case _ => sender ! InsuranceQueryFailed(ex)
        }
    }
  }

  private def createQuote(sender: ActorRef, commandId: String, userId: Long): Unit = {
    val userResp: Future[Any] = userRepo ? GetUser(userId)
    userResp onComplete {
      case Success(user: User) => {
        val addressResp: Future[Any] = (userRepo ? GetAddress(user.addressId.get))
        addressResp onComplete {
          case Success(address: Address) => {
            val insuranceQuote = InsuranceQuoteDBFactory.generateQuote(user, address)
            val newQuote = (insuranceQuotesDB returning insuranceQuotesDB.map(_.quoteId)) +=
              InsuranceQuoteDBFactory.fromInsuranceQuote(insuranceQuote)
            val qResult = db.run(newQuote)
            qResult onComplete {
              case Success(newQuoteId) => sender ! QuoteInsuranceCreated(commandId, newQuoteId)
              case Failure(ex) => sender ! QuoteInsuranceFailed(commandId, ex)
            }
          }
          case Success(error) => sender ! QuoteInsuranceFailed(commandId, error)
          case Failure(ex) => sender ! QuoteInsuranceFailed(commandId, ex)
        }
      }
      case Success(error) => sender ! QuoteInsuranceFailed(commandId, error)
      case Failure(ex) => sender ! QuoteInsuranceFailed(commandId, ex)
    }
  }
}