package com.dataspartan.akka.backend.command.worker.executors

import akka.actor._
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.{FSMState, Failure}
import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.command.MasterWorkerProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressWorkerExecutor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.model.{InsuranceQuotingService, QuoteNotificator, UserRepository}
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect._

object ChangeAddressProtocol {

  case class ChangeAddress(commandId: String, userId: Long, newAddress: Address) extends StartingCommand {
    override def getProps(workerRef: ActorRef): Props =
      ChangeAddressWorkerExecutor.props(workerRef, commandId)
  }

  case class NewAddress(address: Address)
  case class ChangeAddressResult(override val description: String) extends ActionResult
  case class QuoteInsurance(userId: Long)
  case class QuoteInsuranceResult(override val description: String, insuranceQuote: InsuranceQuote) extends ActionResult
  case class NotifyQuote()
  case class EndWork()

  case class ChangeAddressAccepted(commandId: String, result: ActionResult) extends CommandAccepted

  case class ChangeAddressEnd(commandId: String) extends CommandEnd
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
  case object Ended extends ChangeAddressWorkState {
    override def identifier: String = "Ended"
  }

  sealed trait ChangeAddressDomainEvent
  case class AddressChanged(userId: Long, newAddress: Address) extends ChangeAddressDomainEvent
  case class InsuranceQuoteComplete(insuranceQuote: InsuranceQuote) extends ChangeAddressDomainEvent


  def emptyChangeAddressData = ChangeAddressData(None, None, None)
  case class ChangeAddressData(userId: Option[Long], address: Option[Address], insuranceQuote: Option[InsuranceQuote])
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
        changeAddressData copy (insuranceQuote = Some(insuranceQuote))
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
      insuranceService ! QuoteInsurance(data.userId.get)
      goto(QuotingInsurance)
    case Event(StateTimeout, _) =>
      stop(Failure(s"Timeout request in state $stateName"))
  }

  when(QuotingInsurance, stateTimeout = 30 seconds)  {
    case Event(res: QuoteInsuranceResult, _) =>
      log.info("received QuoteInsuranceResult response in state {}", stateName)
      mediator ! DistributedPubSubMediator.Publish(QuoteNotificator.ResultsTopic, res.insuranceQuote)
      goto(Ended) applying InsuranceQuoteComplete(res.insuranceQuote)
    case Event(StateTimeout, data) =>
      log.info(s"Timeout request in state $stateName")
      insuranceService ! QuoteInsurance(data.userId.get)
      stay
  }

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
