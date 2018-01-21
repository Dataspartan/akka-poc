package com.dataspartan.akka.backend.command

import akka.actor.{ActorRef, Props}
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult

object CommandProtocol {

  trait Command {
    val commandId: String
  }

  trait StartingCommand extends Command {
    def getProps(workerRef: ActorRef): Props
  }

  trait CommandAccepted extends Command {
    val result: ActionResult
  }
  trait CommandEnd extends Command

  trait CommandFailed extends Command {
    val error: Throwable
  }
}