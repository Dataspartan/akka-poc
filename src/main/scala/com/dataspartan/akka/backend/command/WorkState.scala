package com.dataspartan.akka.backend.command

import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.command.WorkState.Work
import com.dataspartan.akka.backend.command.MasterWorkerProtocol._

import scala.concurrent.duration.Deadline

object WorkState {

  def empty: WorkState = WorkState(
    acceptedWork = Map.empty,
    doneWorkIds = Set.empty)

  case class Work(command: Command, deadline: Deadline)
}

case class WorkState private (private val acceptedWork:  Map[String, Work],
                                 private val doneWorkIds: Set[String]) {

  def isInProgress(workId: String): Boolean = acceptedWork.contains(workId)
  def isDone(workId: String): Boolean = doneWorkIds.contains(workId)

  def getWork(workId: String): Option[Work] = acceptedWork.get(workId)

  def getAcceptedWork: Iterable[Work] = acceptedWork.values

  def updated(event: WorkDomainEvent): WorkState = event match {
    case WorkAccepted(work: Work) =>
      copy(
        acceptedWork = acceptedWork + (work.command.commandId -> work))

    case WorkTimeout(work: Work) =>
      copy(
        acceptedWork = acceptedWork + (work.command.commandId -> work))

    case WorkIsDone(commandId) =>
      copy(
        acceptedWork = acceptedWork - commandId,
        doneWorkIds = doneWorkIds + commandId)

    case WorkFailed(_) => this
//      copy(
//        acceptedCommands = acceptedCommands - commandId)
  }
}
