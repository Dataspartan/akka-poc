package com.dataspartan.akka.backend.command

import com.dataspartan.akka.backend.command.CommandProtocol.Command

object MasterWorkerProtocol {
  trait WorkDomainEvent
  // Messages from Workers
  case class WorkAccepted(command: Command) extends WorkDomainEvent
  case class WorkIsDone(commandId: String) extends WorkDomainEvent
  case class WorkFailed(commandId: String) extends WorkDomainEvent

  // Messages to Workers
  case class Ack(commandId: String)
}
