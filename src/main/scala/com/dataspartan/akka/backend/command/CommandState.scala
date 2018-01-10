package com.dataspartan.akka.backend.command

import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.command.MasterWorkerProtocol._

object CommandState {

  def empty: CommandState = CommandState(
    acceptedCommands = Map.empty,
    doneCommandIds = Set.empty)
}

case class CommandState private (private val acceptedCommands:  Map[String, Command],
                                 private val doneCommandIds: Set[String]) {

  def isInProgress(commandId: String): Boolean = acceptedCommands.contains(commandId)
  def isDone(commandId: String): Boolean = doneCommandIds.contains(commandId)

  def getCommand(commandId: String): Option[Command] = acceptedCommands.get(commandId)

  def updated(event: WorkDomainEvent): CommandState = event match {
    case WorkAccepted(command) =>
      copy(
        acceptedCommands = acceptedCommands + (command.commandId -> command))

    case WorkIsDone(commandId) =>
      copy(
        acceptedCommands = acceptedCommands - commandId,
        doneCommandIds = doneCommandIds + commandId)

    case WorkFailed(_) => this
//      copy(
//        acceptedCommands = acceptedCommands - commandId)
  }
}
