package com.dataspartan.akka.backend.command

object MasterWorkerProtocol {
  trait WorkDomainEvent
  // Messages from Workers
  case class WorkAccepted(work: Any) extends WorkDomainEvent
  case class WorkTimeout(work: Any) extends WorkDomainEvent
  case class WorkIsDone(workId: String) extends WorkDomainEvent
  case class WorkFailed(workId: String) extends WorkDomainEvent

  // Messages to Workers
  case class Ack(workId: String)
}
