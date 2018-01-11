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

  override def applyEvent(event: ChangeAddressDomainEvent, address: Address): Address = {
    event match {
      case AddressChanged(newAddress) =>
        log.info("apply AddressChanged")
        val nextTick = ThreadLocalRandom.current.nextInt(1, 3).seconds
        timers.startSingleTimer(s"tick", QuoteInsurance, nextTick)
        saveStateSnapshot()
        newAddress
      case InsuranceQuoteComplete =>
        log.info("apply InsuranceQuoteComplete")
        val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
        timers.startSingleTimer(s"tick", NotifyQuote, nextTick)
        saveStateSnapshot()
        address
      case NotifyQuoteComplete =>
        log.info("apply NotifyQuoteComplete")
        val nextTick = ThreadLocalRandom.current.nextInt(10, 30).seconds
        timers.startSingleTimer(s"tick", EndWork, nextTick)
        saveStateSnapshot()
        address
    }
  }

  override def persistenceId: String = commandId

  startWith(Idle, Address("number", s"street", "town", "county", "postcode"))

  when(Idle) {
    case Event(ChangeAddress(_, userId, newAddress), _) => {
      log.info("received ChangeAddress ({}) request for user {} in state {}", newAddress, userId, stateName)
      goto(ChangingAddress) applying AddressChanged(newAddress) forMax (30 seconds) replying ChangeAddressAccepted(commandId)
    }
  }

  when(ChangingAddress) {
    case Event(QuoteInsurance, _) =>
      log.info("received QuoteInsurance request in state {}", stateName)
      goto(QuotingInsurance) applying InsuranceQuoteComplete forMax (30 seconds)
  }

  when(QuotingInsurance) {
    case Event(NotifyQuote, _) =>
      log.info("received NotifyQuote request in state {}", stateName)
      goto(NotifyingQuote) applying NotifyQuoteComplete forMax (30 seconds)
  }

  when(NotifyingQuote) {
    case Event(EndWork, _) =>
      log.info("received EndWork request in state {}", stateName)
      workerRef ! ChangeAddressEnd(commandId)
      goto(Ended) forMax (1 second)
  }

  when(Ended) {
    case Event(StateTimeout, _) =>
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
        stay
    }
  }
}
