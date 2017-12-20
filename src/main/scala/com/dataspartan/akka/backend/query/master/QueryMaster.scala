package com.dataspartan.akka.backend.query.master

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.routing.FromConfig
import com.dataspartan.akka.backend.query.QueryProtocol._
import com.dataspartan.akka.backend.query.{InsuranceQuotingService, UserRepository}


object QueryMaster {
  def props: Props = Props[QueryMaster]
}

class QueryMaster extends Timers with Actor with ActorLogging {

  val userRepoRouter: ActorRef =
    context.actorOf(FromConfig.props(UserRepository.props), "userRepoRouter")

  val insuranceServiceRouter: ActorRef =
    context.actorOf(FromConfig.props(InsuranceQuotingService.props), "insuranceServiceRouter")

  override def receive: Receive = route

  def route: Receive = {
    case msg: UserRepoMsg =>
      log.info("Routing to UserRepository")
      userRepoRouter forward msg
    case msg: InsuranceServiceMsg =>
      log.info("Routing to InsuranceService")
      insuranceServiceRouter forward msg
    case msg => log.info(s"Unknown message: ${msg.getClass.getName}")

  }
}
