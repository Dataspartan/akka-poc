package com.dataspartan.akka.backend.command.worker

import java.util.UUID

import akka.actor.SupervisorStrategy.{Escalate, Restart, Stop}
import akka.actor._
import com.dataspartan.akka.backend.command.ChangeAddressWorkerExecutor
import com.dataspartan.akka.backend.command.MasterWorkerProtocol._
import scala.concurrent.duration._
import com.dataspartan.akka.backend.command.CommandProtocol.{ChangeAddress, Command, CommandAccepted, CommandEnd}
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


  val workerId = UUID.randomUUID().toString

  var currentSender:  Option[ActorRef] = None

  var currentCommand: Option[Command] = None
  def command: Command = currentCommand match {
    case Some(command) => command
    case None         => throw new IllegalStateException("Not working")
  }

  def receive: Receive = idle

  def idle: Receive = {

    case command: Command =>
      log.info("Got Command: {}", command)
      command match {
        case ChangeAddress(commandId, _, _) =>
          log.debug("Create worker for Command: {}", commandId)
          currentCommand = Some(command)
          currentSender = Some(sender())
          val workExecutor = createWorkExecutor(commandId, ChangeAddressWorkerExecutor.props(context.self, commandId))
          workExecutor ! command
          context.become(working)
        case cmd =>
          log.warning(s"(idle) Unhandled command $cmd $sender")
      }

    case msg =>
      log.warning(s"(idle) Unhandled message $msg $sender")
  }

  def working: Receive = {

    case _: CommandAccepted =>
      log.debug("Command is accepted")
      val res = ActionResult("Ok")
      currentSender.get ! res
      commandMasterProxy ! WorkAccepted(command)
    case _: CommandEnd =>
      log.debug("Command is complete")
      commandMasterProxy ! WorkIsDone(command.commandId)
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck())

    case _: Command =>
      log.warning("(working) Master told me to do work, while I'm already working.")

    case msg =>
      log.warning(s"(working) Unhandled message $msg $sender")
  }

  def waitForWorkIsDoneAck(): Receive = {
    case Ack(id) if id == command.commandId =>
      log.debug(s"Received Ack $id, Stopping")
      context.setReceiveTimeout(Duration.Undefined)

    case ReceiveTimeout =>
      log.debug("No ack from master, resending work result")
      commandMasterProxy ! WorkIsDone(command.commandId)

    case _: Terminated =>
      log.debug("Worker stopped")
      context.become(idle)

    case msg =>
      log.warning(s"(waitForWorkIsDoneAck) Unhandled message $msg $sender")
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