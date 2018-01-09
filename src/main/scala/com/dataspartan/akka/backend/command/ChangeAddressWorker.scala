package com.dataspartan.akka.backend.command

import akka.actor.{Actor, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.routing.FromConfig
import com.dataspartan.akka.backend.command.ChangeAddressWorker._
import com.dataspartan.akka.backend.command.CommandProtocol.{ChangeAddress, EndWork, NotifyQuote, QuoteInsurance}
import com.dataspartan.akka.backend.entities.AddressEntities.Address

import scala.concurrent.duration._
import scala.reflect.{ClassTag, _}


object ChangeAddressWorker {

  def props(persistentId: String) = Props(new ChangeAddressWorker(persistentId))


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

//  val userRepoRouter: ActorRef =
//    context.actorOf(FromConfig.props(UserRepository.props), "userRepoRouter")
//
//  val insuranceServiceRouter: ActorRef =
//    context.actorOf(FromConfig.props(InsuranceQuotingService.props), "insuranceServiceRouter")

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def applyEvent(event: DomainEvent, address: Address): Address = {
    event match {
      case AddressChanged(newAddress) => newAddress
      case InsuranceQuoteComplete  => address
      case NotifyQuoteComplete  => address
    }
  }

  override def persistenceId: String = persistentId

  startWith(Idle, Address("number", s"street", "town", "county", "postcode"))

  when(Idle) {
    case Event(ChangeAddress(userId, newAddress), _) => {
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