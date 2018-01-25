package com.dataspartan.akka.bakend.command.worker

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Status}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.dataspartan.akka.backend.command.CommandProtocol._
import com.dataspartan.akka.backend.command.MasterWorkerProtocol.WorkAccepted
import com.dataspartan.akka.backend.command.worker.Worker
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.entities.GeneralEntities.ActionResult
import com.dataspartan.akka.bakend.UtilConfig
import com.dataspartan.akka.bakend.command.worker.WorkerSpec._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.language.postfixOps

object WorkerSpec {

  class Wrapper(target: ActorRef)
    extends Actor {
    def receive = {
      case x => target forward x
    }
  }

  case class TestStartCommand(override val commandId: String, workerExecutor: TestProbe) extends StartingCommand {
    override def getProps(workerRef: ActorRef): Props = Props(new Wrapper(workerExecutor.ref))
  }
  case class TestAcceptedCommand(override val commandId: String, override val result: ActionResult) extends CommandAccepted
  case class TestCommandResult(override val description: String) extends ActionResult
  case class TestCommandEnd(override val commandId: String) extends CommandEnd
}

class WorkerSpec(_system: ActorSystem) extends TestKit(_system)
  with ScalaFutures
  with Matchers
  with Inside
  with FlatSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender {

  def this() = this(ActorSystem("WorkerSpec", UtilConfig.clusterConfig))
  val userId = 0L
  val quoteId = 11L
  val address = Address("26", "Highgate Street", "Forest", "London", "E17 6FD", Some(0L))

  override def beforeAll(): Unit = {
  }

  override def afterAll: Unit = {
    shutdown(system)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
  }

  val master = TestProbe()
  def workerProps = Props(new Worker {
    override val commandMasterProxy: ActorRef = master.ref
  })


  "A Worker Actor" should "execute command" in {
    val workerExecutor = TestProbe()
    val comId = "comId1"
    val worker: ActorRef = system.actorOf(workerProps)
    watch(worker)
    val startCommand = TestStartCommand(comId, workerExecutor)
    worker ! startCommand
    workerExecutor.expectMsg(startCommand)
    val result = TestCommandResult("Result")
    val commandAccepted = TestAcceptedCommand(comId, result)
    worker ! commandAccepted
    expectMsg(result)
    master.expectMsg(WorkAccepted(startCommand))
  }
}