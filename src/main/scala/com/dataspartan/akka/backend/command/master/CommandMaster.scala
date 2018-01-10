package com.dataspartan.akka.backend.command.master

import akka.actor.{ActorLogging, ActorRef, Props, Timers}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.routing.FromConfig
import com.dataspartan.akka.backend.command.CommandProtocol.{Command, CommandAccepted, CommandEnd}
import com.dataspartan.akka.backend.command.{CommandState, MasterWorkerProtocol}
import com.dataspartan.akka.backend.command.MasterWorkerProtocol.{WorkAccepted, WorkDomainEvent, WorkFailed, WorkIsDone}
import com.dataspartan.akka.backend.command.worker.Worker

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  */
object CommandMaster {

  def props(workTimeout: FiniteDuration): Props =
    Props(new CommandMaster(workTimeout))

  case class Ack(workId: String)

  private case object CleanupTick

}

class CommandMaster(workTimeout: FiniteDuration) extends Timers with PersistentActor with ActorLogging {
  import CommandMaster._


  override val persistenceId: String = "command-master"

  val workerRouter: ActorRef =
    context.actorOf(FromConfig.props(Worker.props), "workerRouter")

  val considerWorkerDeadAfter: FiniteDuration =
    context.system.settings.config.getDuration("distributed-workers.consider-worker-dead-after").getSeconds.seconds
  def newStaleWorkerDeadline(): Deadline = considerWorkerDeadAfter.fromNow

  timers.startPeriodicTimer("cleanup", CleanupTick, workTimeout / 2)

//  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  // commandState is event sourced to be able to make sure work is processed even in case of crash
  private var commandState = CommandState.empty



  override def receiveRecover: Receive = {

    case SnapshotOffer(_, commandStateSnapshot: CommandState) =>
      // If we would have  logic triggering snapshots in the actor
      // we would start from the latest snapshot here when recovering
      log.info("Got snapshot command state")
      commandState = commandStateSnapshot

    case event: WorkDomainEvent =>
      // only update current state by applying the event, no side effects
      commandState = commandState.updated(event)
      log.info("Replayed {}", event.getClass.getSimpleName)

    case RecoveryCompleted =>
      log.info("Recovery completed")

  }

  override def receiveCommand: Receive = {

    // #persisting
    case command: Command =>
      // idempotent
      if (!commandState.isDone(command.commandId)) {
        log.info("Start command: {}", command.commandId)
        persist(WorkAccepted(command)) { event =>
          commandState = commandState.updated(event)
        }
      }
    // #persisting


    case msg: WorkIsDone =>
      // idempotent - redelivery from the worker may cause duplicates, so it needs to be
      if (commandState.isDone(msg.commandId)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(msg.commandId)
      } else if (!commandState.isInProgress(msg.commandId)) {
        log.info("Command {} not in progress, reported as done by worker", msg.commandId)
      } else {
        log.info("Command {} is done by worker", msg.commandId)
        persist(msg) { event =>
          commandState = commandState.updated(event)
          // Ack back to original sender
          sender ! MasterWorkerProtocol.Ack( msg.commandId)
        }
      }

    case msg: WorkFailed =>
      if (commandState.isInProgress(msg.commandId)) {
        log.info("Command {} failed by worker", msg.commandId)
        persist(msg) { event =>
          commandState = commandState.updated(event)
          startWork(commandState.getCommand(msg.commandId).get)
        }
      }




//    // #pruning
//    case CleanupTick =>
//      log.info("Cleaning: {}", workers.size)
//      workers.foreach {
//        case (workerId, WorkerState(_, Busy(workId, timeout), _)) if timeout.isOverdue() =>
//          log.info("Work timed out: {}", workId)
//          workers -= workerId
//          persist(WorkerTimedOut(workId)) { event ⇒
//            workState = workState.updated(event)
//            notifyWorkers()
//          }
//
//
//        case (workerId, WorkerState(_, Idle, lastHeardFrom)) if lastHeardFrom.isOverdue() =>
//          log.info("Too long since heard from worker {}, pruning", workerId)
//          workers -= workerId
//
//        case _ => // this one is a keeper!
//      }
    // #pruning
  }

  def startWork(command: Command): Unit = {
    workerRouter forward command
  }

  def tooLongSinceHeardFrom(lastHeardFrom: Long) =
    System.currentTimeMillis() - lastHeardFrom > considerWorkerDeadAfter.toMillis

}
