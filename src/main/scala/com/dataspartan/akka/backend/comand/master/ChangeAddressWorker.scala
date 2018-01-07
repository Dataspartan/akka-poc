package com.dataspartan.akka.backend.comand.master

import java.util.UUID

import akka.actor._

import scala.concurrent.duration._
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.util.Timeout
import akka.pattern.ask
import com.dataspartan.akka.backend.comand.master.ChangeAddressWorker.{DomainEvent, WorkState}

import scala.reflect._
import scala.reflect.ClassTag
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}



object ChangeAddressWorker {

  def props(persistentId: String) = Props(new ChangeAddressWorker(persistentId))

  sealed trait Command
  case class ChangeAddress(newAddress: Address) extends Command
  case object QuoteInsurance extends Command
  case object NotifyQuote extends Command
  case object EndWork extends Command

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

class ChangeAddressWorker(persistentId: String) extends Actor with PersistentFSM[WorkState, Address, DomainEvent] {
  import ChangeAddressWorker._

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def applyEvent(event: DomainEvent, address: Address): Address = {
    event match {
      case AddressChanged(newAddress) => newAddress
      case InsuranceQuoteComplete  => address
      case NotifyQuoteComplete  => address
    }
  }

  override def persistenceId: String = persistentId

  startWith(Idle, _)

  when(Idle) {
    case Event(ChangeAddress(newAddress), _) => {
      log.debug("received ChangeAddress ({}) request in state {}", newAddress, stateName)
      goto(ChangingAddress) applying AddressChanged(newAddress) forMax (1 seconds)
    }
  }

  when(ChangingAddress) {
    case Event(QuoteInsurance, _) => {
      log.debug("received QuoteInsurance request in state {}", stateName)
      goto(QuotingInsurance) applying InsuranceQuoteComplete forMax (1 seconds)
    }
  }

  when(QuotingInsurance) {
    case Event(NotifyQuote, _) => {
      log.debug("received NotifyQuote request in state {}", stateName)
      goto(NotifyingQuote) applying NotifyQuoteComplete forMax (1 seconds)
    }
  }

  when(NotifyingQuote) {
    case Event(EndWork, _) => {
      log.debug("received EndWork request in state {}", stateName)
      stop()
    }
  }

  whenUnhandled {
    // common code for both states
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }
}