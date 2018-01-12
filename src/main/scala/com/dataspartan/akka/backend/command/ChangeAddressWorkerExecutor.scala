package com.dataspartan.akka.backend.command

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.util.Timeout
import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities.Address

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.reflect.{ClassTag, _}
import akka.pattern.ask
import akka.persistence.SaveSnapshotSuccess
import com.dataspartan.akka.backend.command.ChangeAddressWorkerExecutor.{ChangeAddressDomainEvent, ChangeAddressWorkState}

import scala.language.postfixOps

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

}

class ChangeAddressWorkerExecutor(workerRef: ActorRef, commandId: String) extends Actor with PersistentFSM[ChangeAddressWorkState, Address, ChangeAddressDomainEvent]
  with ActorLogging with Timers {

  import ChangeAddressWorkerExecutor._

  //  val userRepoRouter: ActorRef =
  //    context.actorOf(FromConfig.props(UserRepository.props), "userRepoRouter")
  //
  //  val insuranceServiceRouter: ActorRef =
  //    context.actorOf(FromConfig.props(InsuranceQuotingService.props), "insuranceServiceRouter")

  override def domainEventClassTag: ClassTag[ChangeAddressDomainEvent] = classTag[ChangeAddressDomainEvent]

  override def persistenceId: String = commandId

  override def applyEvent(event: ChangeAddressDomainEvent, address: Address): Address = {
    event match {
      case AddressChanged(newAddress) =>
        log.info("apply AddressChanged")
        execChangeAddress(newAddress)
      case InsuranceQuoteComplete =>
        log.info("apply InsuranceQuoteComplete")
        execQuoteInsurance(address)
      case NotifyQuoteComplete =>
        log.info("apply NotifyQuoteComplete")
        execNotifyQuote(address)
    }
  }

  def execChangeAddress(newAddress: Address): Address = {
    val nextTick = ThreadLocalRandom.current.nextInt(1, 3).seconds
    timers.startSingleTimer(s"tick", QuoteInsurance, nextTick )
    newAddress
  }

  def execQuoteInsurance(newAddress: Address): Address = {
    val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
    timers.startSingleTimer(s"tick", NotifyQuote, nextTick)
    newAddress
  }

  def execNotifyQuote(newAddress: Address): Address = {
    val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
    timers.startSingleTimer(s"tick", EndWork, nextTick)
    newAddress
  }


  startWith(Idle, Address("number", s"street", "town", "county", "postcode"))

  when(Idle) {
    case Event(ChangeAddress(_, userId, newAddress), _) => {
      log.info("received ChangeAddress ({}) request for user {} in state {}", newAddress, userId, stateName)
      goto(ChangingAddress) applying AddressChanged(newAddress) replying ChangeAddressAccepted(commandId)
    }
  }

  when(ChangingAddress, stateTimeout = 30 seconds) {
    case Event(QuoteInsurance, _) | Event(StateTimeout, _) =>
      log.info("received QuoteInsurance request in state {}", stateName)
      goto(QuotingInsurance) applying InsuranceQuoteComplete
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
