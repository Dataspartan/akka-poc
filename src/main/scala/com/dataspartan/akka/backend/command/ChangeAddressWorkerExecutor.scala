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
import com.dataspartan.akka.backend.command.ChangeAddressWorkerExecutor.{DomainEvent, WorkState}

object ChangeAddressWorkerExecutor {

  def props(workerRef: ActorRef, commandId: String) = Props(new ChangeAddressWorkerExecutor(workerRef, commandId))

  sealed trait WorkState extends FSMState
  case object Idle extends WorkState {
    override def identifier: String = "Idle"
  }
  case object ChangingAddress extends WorkState {
    override def identifier: String = "ChangingAddress"
  }
  case object QuotingInsurance extends WorkState {
    override def identifier: String = "QuotingInsurance"
  }
  case object NotifyingQuote extends WorkState {
    override def identifier: String = "NotifyingQuote"
  }

  sealed trait DomainEvent
  case class AddressChanged(newAddress: Address) extends DomainEvent
  case object InsuranceQuoteComplete extends DomainEvent
  case object NotifyQuoteComplete extends DomainEvent

}

class ChangeAddressWorkerExecutor(workerRef: ActorRef, commandId: String) extends Actor with PersistentFSM[WorkState, Address, DomainEvent]
  with ActorLogging with Timers {

  import ChangeAddressWorkerExecutor._

  //  val userRepoRouter: ActorRef =
  //    context.actorOf(FromConfig.props(UserRepository.props), "userRepoRouter")
  //
  //  val insuranceServiceRouter: ActorRef =
  //    context.actorOf(FromConfig.props(InsuranceQuotingService.props), "insuranceServiceRouter")

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def applyEvent(event: DomainEvent, address: Address): Address = {
    event match {
      case AddressChanged(newAddress) => {
        log.info("apply AddressChanged")
        val nextTick = ThreadLocalRandom.current.nextInt(3, 10).seconds
        timers.startSingleTimer(s"tick", QuoteInsurance, nextTick)
        saveStateSnapshot()
        newAddress
      }
      case InsuranceQuoteComplete => {
        log.info("apply InsuranceQuoteComplete")
        val nextTick = ThreadLocalRandom.current.nextInt(3, 10).seconds
        timers.startSingleTimer(s"tick", NotifyQuote, nextTick)
        saveStateSnapshot()
        address
      }
      case NotifyQuoteComplete => {
        log.info("apply NotifyQuoteComplete")
        val nextTick = ThreadLocalRandom.current.nextInt(3, 10).seconds
        timers.startSingleTimer(s"tick", EndWork, nextTick)
        saveStateSnapshot()
        address
      }
    }
  }

  override def persistenceId: String = commandId

  startWith(Idle, Address("number", s"street", "town", "county", "postcode"))

  when(Idle) {
    case Event(ChangeAddress(commandId, userId, newAddress), _) => {
      log.info("received ChangeAddress ({}) request for user {} in state {}", newAddress, userId, stateName)
      goto(ChangingAddress) applying AddressChanged(newAddress) forMax (30 seconds) replying ChangeAddressAccepted
    }
  }

  when(ChangingAddress) {
    case Event(QuoteInsurance, _) => {
      log.info("received QuoteInsurance request in state {}", stateName)
      goto(QuotingInsurance) applying InsuranceQuoteComplete forMax (30 seconds)
    }
  }

  when(QuotingInsurance) {
    case Event(NotifyQuote, _) => {
      log.info("received NotifyQuote request in state {}", stateName)
      goto(NotifyingQuote) applying NotifyQuoteComplete forMax (30 seconds)
    }
  }

  when(NotifyingQuote) {
    case Event(EndWork, _) => {
      log.info("received EndWork request in state {}", stateName)
      workerRef ! ChangeAddressEnd
      stop
    }
  }

  whenUnhandled {
    // common code for both states
    case Event(e, s) =>
      log.info(s"worker executor persistentId $persistenceId")
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  log.info(s"Starting worker executor with persistentId $persistenceId and state $stateName")
}
