package com.dataspartan.akka.backend.command.worker.executors

import akka.actor._
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.{FSMState, Failure}
import com.dataspartan.akka.backend.command.CommandProtocol.{Command, _}
import com.dataspartan.akka.backend.command.MasterWorkerProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressWorkerExecutor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.model.{InsuranceQuotingService, QuoteNotificator, UserRepository}
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote
import com.dataspartan.akka.backend.entities.UserEntities.User
import com.dataspartan.akka.backend.query.QueryProtocol.GetInsuranceQuote

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect._
import akka.pattern.ask

object ChangeAddressProtocol {

  case class ChangeAddress(override val commandId: String, userId: Long, newAddress: Address) extends StartingCommand {
    override def getProps(workerRef: ActorRef): Props =
      ChangeAddressWorkerExecutor.props(workerRef, commandId)
  }
  case class ChangeAddressResult(override val description: String) extends ActionResult

  case class NewUser(override val commandId: String, user: User) extends Command
  case class NewUserCreated(override val commandId: String, userId: Long) extends CommandEnd

  case class NewAddress(override val commandId: String, address: Address) extends Command
  case class NewAddressCreated(override val commandId: String, addressId: Long) extends CommandEnd

  case class QuoteInsurance(override val commandId: String, userId: Long) extends Command
  case class QuoteInsuranceCreated(override val commandId: String, quoteId: Long) extends CommandEnd
  case class QuoteInsuranceResult(override val description: String, insuranceQuote: InsuranceQuote) extends ActionResult
  case class NotifyQuote()
  case class EndWork()

  case class ChangeAddressAccepted(override val commandId: String, override val result: ActionResult) extends CommandAccepted

  case class ChangeAddressEnd(override val commandId: String) extends CommandEnd
}

object ChangeAddressWorkerExecutor {

  def props(workerRef: ActorRef, commandId: String) = Props(new ChangeAddressWorkerExecutor(workerRef, commandId))

  sealed trait ChangeAddressWorkState extends FSMState
  case object Idle extends ChangeAddressWorkState {
    override def identifier: String = "Idle"
  }
  case object ChangingAddress extends ChangeAddressWorkState {
    override def identifier: String = "ChangingAddress"
  }
  case object QuotingInsurance extends ChangeAddressWorkState {
    override def identifier: String = "QuotingInsurance"
  }
  case object NotifyingQuote extends ChangeAddressWorkState {
    override def identifier: String = "NotifyingQuote"
  }
  case object QuotingError extends ChangeAddressWorkState {
    override def identifier: String = "NotifyingQuote"
  }
  case object Ended extends ChangeAddressWorkState {
    override def identifier: String = "Ended"
  }

  sealed trait ChangeAddressDomainEvent
  case class AddressChanged(userId: Long, newAddress: Address) extends ChangeAddressDomainEvent
  case class InsuranceQuoteComplete(insuranceQuote: QuoteInsuranceCreated) extends ChangeAddressDomainEvent


  def emptyChangeAddressData = ChangeAddressData(None, None, None)
  case class ChangeAddressData(userId: Option[Long], address: Option[Address], insuranceQuoteId: Option[Long])
}

class ChangeAddressWorkerExecutor(workerRef: ActorRef, commandId: String) extends Actor
  with PersistentFSM[ChangeAddressWorkState, ChangeAddressData, ChangeAddressDomainEvent]
  with ActorLogging with Timers {

  import ChangeAddressWorkerExecutor._

  val userRepo: ActorRef = context.actorOf(UserRepository.props, "userRepo")

  val insuranceService: ActorRef = context.actorOf(InsuranceQuotingService.props, "insuranceService")

  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  override def domainEventClassTag: ClassTag[ChangeAddressDomainEvent] = classTag[ChangeAddressDomainEvent]

  override def persistenceId: String = commandId

  override def applyEvent(event: ChangeAddressDomainEvent, changeAddressData: ChangeAddressData): ChangeAddressData = {
    event match {
      case AddressChanged(userId, newAddress) =>
        log.info("apply AddressChanged")
        changeAddressData copy (userId = Some(userId), address = Some(newAddress))
      case InsuranceQuoteComplete(insuranceQuote) =>
        log.info("apply InsuranceQuoteComplete")
        changeAddressData copy (insuranceQuoteId = Some(insuranceQuote.quoteId))
    }
  }

  startWith(Idle, emptyChangeAddressData)

  when(Idle) {
    case Event(changeAddress: ChangeAddress, _) =>
      log.info("received ChangeAddress ({}) request for user {} in state {}",
        changeAddress.newAddress, changeAddress.userId, stateName)
      userRepo ! changeAddress
      goto(ChangingAddress) applying AddressChanged(changeAddress.userId, changeAddress.newAddress)
  }

  when(ChangingAddress, stateTimeout = 5 seconds) {
    case Event(res: ChangeAddressResult, data) =>
      log.info("received ChangeAddressResult response in state {}", stateName)
      workerRef ! ChangeAddressAccepted(commandId, res)
      insuranceService ! QuoteInsurance(commandId, data.userId.get)
      goto(QuotingInsurance)
//    case Event(failure: ChangeAddressFailed, data) =>
//      log.info("received ChangeAddressFailed response in state {}", stateName)
//      workerRef ! failure
//      goto(Ended)
    case Event(StateTimeout, _) =>
      stop(Failure(s"Timeout request in state $stateName"))
  }

  when(QuotingInsurance, stateTimeout = 30 seconds)  {
    case Event(res: QuoteInsuranceCreated, _) =>
      log.info("received QuoteInsuranceResult response in state {}", stateName)
      mediator ! DistributedPubSubMediator.Publish(QuoteNotificator.ResultsTopic, res)
      goto(Ended) applying InsuranceQuoteComplete(res)
//    case Event(failure: QuoteInsuranceFailed, data) =>
//      log.info("received QuoteInsuranceFailed response in state {}", stateName)
//      goto(QuotingError)
    case Event(StateTimeout, data) =>
      log.info(s"Timeout request in state $stateName")
      insuranceService ! QuoteInsurance(commandId, data.userId.get)
      stay
  }

//  when(QuotingError)  {
//    case Event(quoteCommand: QuoteInsurance, _) =>
//      log.info("received ChangeAddressResult response in state {}", stateName)
//      insuranceService ! quoteCommand
//      goto(QuotingInsurance)
//    case Event(StateTimeout, data) =>
//      log.info(s"Timeout request in state $stateName")
//      stay
//  }

//  when(NotifyingQuote) {
//    case Event(StateTimeout, _) =>
//      log.info("received EndWork request in state {}", stateName)
//      goto(Ended)
//  }

  when(Ended, stateTimeout = 5 seconds) {
    case Event(StateTimeout, _) =>
      log.info("Sending End not in state {}", stateName)
      workerRef ! ChangeAddressEnd(commandId)
      stay
    case Event(MasterWorkerProtocol.Ack(_), _) =>
      log.info("Stopping")
      stop
  }

  whenUnhandled {
    // common code for both states
    case Event(e, s) => e match {
      case _: SaveSnapshotSuccess =>
        stay
      case _ =>
        log.warning("received unhandled request {} for persistenceId {} in state {}/{}", e, persistenceId, stateName, s)
        stay forMax (0 seconds)
    }
  }
}
