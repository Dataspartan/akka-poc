package com.dataspartan.akka.backend.command.worker

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor._
import com.dataspartan.akka.backend.command.CommandProtocol.StartingCommand
import com.dataspartan.akka.backend.command.MasterWorkerProtocol._
//import scala.concurrent.duration._
import com.dataspartan.akka.backend.command.CommandProtocol.{Command, CommandAccepted, CommandEnd}
import com.dataspartan.akka.backend.command.master.CommandMasterSingleton
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult

import scala.language.postfixOps

/**
  * The worker is actually more of a middle manager, delegating the actual work
  * to the WorkExecutor, supervising it and keeping itself available to interact with the work master.
  */
object Worker {

  def props: Props = Props[Worker]
}

class Worker extends Actor with Timers with ActorLogging {

  val commandMasterProxy: ActorRef = context.actorOf(
    CommandMasterSingleton.proxyProps(context.system), name = "commandMasterProxy")

  val workerId: String = UUID.randomUUID().toString

  var currentWorkExecutor:  Option[ActorRef] = None
  var currentSender:  Option[ActorRef] = None
  var currentCommand: Option[Command] = None
  def command: Command = currentCommand match {
    case Some(command) => command
    case None         => throw new IllegalStateException("Not working")
  }

  def receive: Receive = {

    case commOk: CommandAccepted =>
      log.debug("Command is accepted")
      currentSender.get ! commOk.result
      commandMasterProxy ! WorkAccepted(command)

    case _: CommandEnd =>
      log.debug("Command is complete")
      commandMasterProxy ! WorkIsDone(command.commandId)

    case command: Command  =>
      log.info("Got Command: {}", command)
      command match {
        case sCommand: StartingCommand =>
          executeCommand(sCommand, sCommand.getProps(self))
      }
      if (currentWorkExecutor.isDefined) {
        log.debug("Send command to executor: {}", command.commandId)
        currentWorkExecutor.get ! command
      }

    case _: Terminated =>
      log.debug("Worker stopped")
      currentWorkExecutor = None
      currentSender = None
      currentCommand = None

    case msg =>
      if (currentWorkExecutor.isDefined) {
        log.debug("Send msg to executor: {}", msg)
        currentWorkExecutor.get ! msg
      }
      else {
        log.warning(s"Unhandled message $msg $sender")
      }
  }

  def executeCommand(command: Command, props: Props): Unit = {
    if (currentCommand.isEmpty) {
      log.debug("Create worker for Command: {}", command.commandId)
      currentCommand = Some(command)
      currentSender = Some(sender())
      currentWorkExecutor = Some(createWorkExecutor( command.commandId, props))
    }
  }

  def createWorkExecutor(commandId: String, props: Props): ActorRef =
    context.watch(context.actorOf(props, workerId))

  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: Exception =>
      currentCommand foreach { command => commandMasterProxy ! WorkFailed(command.commandId) }
      Restart
  }
}