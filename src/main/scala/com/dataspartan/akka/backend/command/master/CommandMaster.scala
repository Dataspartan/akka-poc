package com.dataspartan.akka.backend.command.master

import akka.actor.{ActorLogging, ActorRef, Props, Timers}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.routing.FromConfig
import com.dataspartan.akka.backend.command.CommandProtocol.Command
import com.dataspartan.akka.backend.command.WorkState.Work
import com.dataspartan.akka.backend.command.{MasterWorkerProtocol, WorkState}
import com.dataspartan.akka.backend.command.MasterWorkerProtocol._
import com.dataspartan.akka.backend.command.worker.Worker

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  */
object CommandMaster {

  def props(workTimeout: FiniteDuration): Props =
    Props(new CommandMaster(workTimeout))

  private case object CheckingTick
}

class CommandMaster(workTimeout: FiniteDuration) extends Timers with PersistentActor with ActorLogging {
  import CommandMaster._

  override val persistenceId: String = "command-master"

  val workerRouter: ActorRef =
    context.actorOf(FromConfig.props(Worker.props), "workerRouter")


  timers.startPeriodicTimer("cleanup", CheckingTick, workTimeout / 2)

//  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  // workState is event sourced to be able to make sure work is processed even in case of crash
  private var workState = WorkState.empty

  override def receiveRecover: Receive = {

    case SnapshotOffer(_, workStateSnapshot: WorkState) =>
      // If we would have  logic triggering snapshots in the actor
      // we would start from the latest snapshot here when recovering
      log.debug("Got snapshot command state")
      workState = workStateSnapshot

    case event: WorkDomainEvent =>
      // only update current state by applying the event, no side effects
      workState = workState.updated(event)
      log.debug("Replayed {}", event.getClass.getSimpleName)

    case RecoveryCompleted =>
      log.debug("Recovery completed")

  }

  override def receiveCommand: Receive = {

    case command: Command =>
      log.debug("Command")
      // idempotent
      if (!workState.isDone(command.commandId)) {
        log.info("Start command: {}", command.commandId)
        startWork(command)
      }

    // #persisting
    case workAccepted: WorkAccepted =>
      log.debug("WorkAccepted")
      val command: Command = workAccepted.work.asInstanceOf[Command]
      // idempotent
      if (!workState.isDone(command.commandId)) {
        log.info("Command accepted: {}", command.commandId)

        persist(WorkAccepted(Work(command, Deadline.now + workTimeout))) { event =>
          workState = workState.updated(event)
        }
      }
    // #persisting

    case msg: WorkIsDone =>
      log.debug("WorkIsDone")
      // idempotent - redelivery from the worker may cause duplicates, so it needs to be
      if (workState.isDone(msg.workId)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(msg.workId)
      } else if (!workState.isInProgress(msg.workId)) {
        log.info("Command {} not in progress, reported as done by worker", msg.workId)
      } else {
        log.info("Command {} is done by worker", msg.workId)
        persist(msg) { event =>
          workState = workState.updated(event)
          sender() ! MasterWorkerProtocol.Ack(msg.workId)
        }
      }

    case msg: WorkFailed =>
      log.debug("WorkFailed")
      if (workState.isInProgress(msg.workId)) {
        log.info("Command {} failed by worker", msg.workId)
        persist(msg) { event =>
          workState = workState.updated(event)
          startWork(workState.getWork(msg.workId).get.command)
        }
      }

    // #pruning
    case CheckingTick =>
      log.debug("Checking work")
      workState.getAcceptedWork.foreach {
        case Work(command, timeout) if timeout.isOverdue() =>
          log.info("Work timed out: {}, restarting", command)
          persist(WorkTimeout(Work(command, Deadline.now + workTimeout))) { event =>
            workState = workState.updated(event)
            startWork(command)
          }

        case _ => // this one is a keeper!
      }
    // #pruning

    case msg =>
      log.warning(s"Unhandled message $msg $sender")
  }

  def startWork(command: Command): Unit = {
    workerRouter forward command
  }
}
