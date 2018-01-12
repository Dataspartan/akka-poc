package com.dataspartan.akka.backend.command.worker.executors

import java.util.concurrent.ThreadLocalRandom

import akka.actor._
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.{FSMState, Failure}
import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.command.MasterWorkerProtocol
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressWorkerExecutor._
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.model.{InsuranceQuotingService, UserRepository}
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect._

object ChangeAddressProtocol {

  case class ChangeAddress(commandId: String, userId: String, newAddress: Address) extends StartingCommand {
    override def getProps(workerRef: ActorRef): Props =
      ChangeAddressWorkerExecutor.props(workerRef, commandId)
  }

  case class QuoteInsurance(commandId: String) extends Command
  case class NotifyQuote(commandId: String) extends Command
  case class EndWork(commandId: String) extends Command


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
  case class AddressChanged(newAddress: Address) extends ChangeAddressDomainEvent
  case object InsuranceQuoteComplete extends ChangeAddressDomainEvent
  case object NotifyQuoteComplete extends ChangeAddressDomainEvent


  def emptyChangeAddressData = ChangeAddressData(None)
  case class ChangeAddressData(address: Option[Address])
}

class ChangeAddressWorkerExecutor(workerRef: ActorRef, commandId: String) extends Actor
  with PersistentFSM[ChangeAddressWorkState, ChangeAddressData, ChangeAddressDomainEvent]
  with ActorLogging with Timers {

  import ChangeAddressWorkerExecutor._

    val userRepo: ActorRef =
      context.actorOf(UserRepository.props, "userRepo")

    val insuranceService: ActorRef =
      context.actorOf(InsuranceQuotingService.props, "insuranceService")

  override def domainEventClassTag: ClassTag[ChangeAddressDomainEvent] = classTag[ChangeAddressDomainEvent]

  override def persistenceId: String = commandId

  override def applyEvent(event: ChangeAddressDomainEvent, changeAddressData: ChangeAddressData): ChangeAddressData = {
    event match {
      case AddressChanged(newAddress) =>
        log.info("apply AddressChanged")
        self ! NotifyQuote
        ChangeAddressData(Some(newAddress))
      case InsuranceQuoteComplete =>
        log.info("apply InsuranceQuoteComplete")
        execQuoteInsurance(changeAddressData)
      case NotifyQuoteComplete =>
        log.info("apply NotifyQuoteComplete")
        execNotifyQuote(changeAddressData)
    }
  }

  def execQuoteInsurance(changeAddressData: ChangeAddressData): ChangeAddressData = {
    val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
    timers.startSingleTimer(s"tick", NotifyQuote, nextTick)
    changeAddressData
  }

  def execNotifyQuote(changeAddressData: ChangeAddressData): ChangeAddressData = {
    val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
    timers.startSingleTimer(s"tick", EndWork, nextTick)
    changeAddressData
  }

  startWith(Idle, emptyChangeAddressData)

  when(Idle) {
    case Event(changeAddress: ChangeAddress, _) =>
      log.info("received ChangeAddress ({}) request for user {} in state {}",
        changeAddress.newAddress, changeAddress.userId, stateName)
      userRepo ! changeAddress
      goto(ChangingAddress) applying AddressChanged(changeAddress.newAddress)
  }

  when(ChangingAddress, stateTimeout = 5 seconds) {
    case Event(res: ActionResult, _) =>
      log.info("received ActionResult response in state {}", stateName)
      workerRef ! ChangeAddressAccepted(commandId, res)
      goto(QuotingInsurance) applying InsuranceQuoteComplete
    case Event(StateTimeout, _) =>
      stop(Failure(s"Timeout request in state $stateName"))
  }

  when(QuotingInsurance, stateTimeout = 30 seconds)  {
    case Event(NotifyQuote, _) | Event(StateTimeout, _) =>
      log.info("received NotifyQuote request in state {}", stateName)
      goto(NotifyingQuote) applying NotifyQuoteComplete
  }

  when(NotifyingQuote, stateTimeout = 30 seconds) {
    case Event(EndWork, _) | Event(StateTimeout, _) =>
      log.info("received EndWork request in state {}", stateName)
      goto(Ended)
  }

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
