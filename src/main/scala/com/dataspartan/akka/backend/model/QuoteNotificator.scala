package com.dataspartan.akka.backend.model

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote

object QuoteNotificator {
  def props: Props = Props(new QuoteNotificator)

  val ResultsTopic = "results"
}

// #work-result-consumer
class QuoteNotificator extends Actor with ActorLogging {

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(QuoteNotificator.ResultsTopic, self)

  def receive = {
    case _: DistributedPubSubMediator.SubscribeAck =>
    case insuranceQuote: InsuranceQuote =>
      log.info("Consumed result: {}", insuranceQuote)
  }

}