package com.dataspartan.akka.backend.command.worker

import java.util.UUID

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.util.Timeout
import com.dataspartan.akka.backend.command.ChangeAddressWorkerExecutor
import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.command.MasterWorkerProtocol._
import com.dataspartan.akka.backend.entities.AddressEntities.Address

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import com.dataspartan.akka.backend.command.master.CommandMasterSingleton
import com.dataspartan.akka.http.HttpRestServer.system

/**
  * The worker is actually more of a middle manager, delegating the actual work
  * to the WorkExecutor, supervising it and keeping itself available to interact with the work master.
  */
object Worker {

  def props(): Props = Props[Worker]
}

class Worker extends Actor with Timers with ActorLogging {
  import context.dispatcher

  val workerId = UUID.randomUUID().toString

  val masterProxy: ActorRef = system.actorOf(
    CommandMasterSingleton.proxyProps(system), name = "commandMasterProxy")

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
          log.info("Create worker for Command: {}", commandId)
          currentCommand = Some(command)
          val workExecutor = createWorkExecutor(commandId, ChangeAddressWorkerExecutor.props(context.self, commandId))
          workExecutor ! command
          context.become(working)

        case msg => log.info("Message error: {}", msg.getClass.getName)
      }

    case msg => log.info("Message error: {}", msg.getClass.getName)

  }

  def working: Receive = {

    case _: CommandAccepted =>
      log.info("Command is accepted")
      masterProxy ! WorkAccepted(command)
    case _: CommandEnd =>
      log.info("Command is complete")
      masterProxy ! WorkIsDone(command.commandId)
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck())

    case _: Command =>
      log.warning("Yikes. Master told me to do work, while I'm already working.")

  }

  def waitForWorkIsDoneAck(): Receive = {
    case Ack(id) if id == command.commandId =>
      log.info(s"Received Ack $id, Stopping")
      Stop

    case ReceiveTimeout =>
      log.info("No ack from master, resending work result")
      masterProxy ! WorkIsDone(command.commandId)
  }

  def createWorkExecutor(commandId: String, props: Props): ActorRef =
  // in addition to starting the actor we also watch it, so that
  // if it stops this worker will also be stopped
    context.watch(context.actorOf(props, s"work-executor-$workerId"))

  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: Exception =>
      currentCommand foreach { command => masterProxy ! WorkFailed(command.commandId) }
      Stop
  }
}


object WorkerTest extends App {

  val system = ActorSystem("ClusterSystem")
  implicit val executionContext: ExecutionContext = system.dispatcher

  val commandMasterProxy: ActorRef = system.actorOf(
    CommandMasterSingleton.proxyProps(system), name = "commandMasterProxy")

  val req = ChangeAddress("id2112111", "uid", Address("number", s"street", "town", "county", "postcode"))

  /*
   * Start a new [[Requester]] actor.
   */
  val actorFsm: ActorRef = system.actorOf(Worker.props)
  implicit def timeout: Timeout = 5 seconds

  actorFsm ! req



//  val result: Future[ChangeAddressResult] =
//    (actorFsm ? req).mapTo[ChangeAddressResult]
//
//  result.onComplete( content =>
//    println(s"--> $actorFsm - Content result($content)")
//  )

  //  actorFsm ! AddItem(Item(UUID.randomUUID().toString, "itemName2", 1))




  //  val cart: Future[ShoppingCart] =
  //    (actorFsm ? GetCurrentCart).mapTo[ShoppingCart]
  //
  //  cart.onComplete( content =>
  //    println(s"--> $actorFsm - Content cart($content)")
  //  )
  //
  //  val actorFsm2 = system.actorOf(FsmShoppingCart.props("id2"))
  //
  //  val cart2: Future[ShoppingCart] =
  //    (actorFsm2 ? GetCurrentCart).mapTo[ShoppingCart]
  //
  //  cart2.onComplete( content =>
  //    println(s"--> $actorFsm2 - Content cart($content)")
  //  )
}