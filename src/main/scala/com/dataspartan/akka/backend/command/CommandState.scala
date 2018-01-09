package com.dataspartan.akka.backend.command

import com.dataspartan.akka.backend.command.CommandProtocol.Command

import scala.collection.immutable.Queue

object CommandState {

  def empty: CommandState = CommandState(
    pendingCommand = Queue.empty,
    commandInProgress = Map.empty,
    doneCommandIds = Set.empty)
}

case class CommandState private(
                               private val pendingCommand: Queue[Command],
                               private val commandInProgress: Map[String, Command],
                               private val doneCommandIds: Set[String]) {


  def hasCommand: Boolean = pendingCommand.nonEmpty
  def nextCommand: Command = pendingCommand.head
  def isInProgress(commandId: String): Boolean = commandInProgress.contains(commandId)
  def isDone(commandId: String): Boolean = doneCommandIds.contains(commandId)

//  def updated(event: WorkDomainEvent): CommandState = event match {
//    case WorkAccepted(work) ⇒
//      copy(
//        pendingWork = pendingWork enqueue work,
//        acceptedWorkIds = acceptedWorkIds + work.workId)
//
//    case WorkStarted(workId) ⇒
//      val (work, rest) = pendingWork.dequeue
//      require(workId == work.workId, s"WorkStarted expected workId $workId == ${work.workId}")
//      copy(
//        pendingWork = rest,
//        workInProgress = workInProgress + (workId -> work))
//
//    case WorkCompleted(workId, result) ⇒
//      copy(
//        workInProgress = workInProgress - workId,
//        doneWorkIds = doneWorkIds + workId)
//
//    case WorkerFailed(workId) ⇒
//      copy(
//        pendingWork = pendingWork enqueue workInProgress(workId),
//        workInProgress = workInProgress - workId)
//
//    case WorkerTimedOut(workId) ⇒
//      copy(
//        pendingWork = pendingWork enqueue workInProgress(workId),
//        workInProgress = workInProgress - workId)
//  }

}
