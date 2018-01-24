package com.dataspartan.akka.bakend.command.worker.executor

import akka.actor.{ActorRef, ActorSystem, Props, Status}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{CurrentTopics, GetTopics, Subscribe, SubscribeAck}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol._
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressWorkerExecutor
import com.dataspartan.akka.backend.entities.AddressEntities._
import com.dataspartan.akka.backend.model.ModelExceptions.DataAccessException
import com.dataspartan.akka.backend.model.QuoteNotificator
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._

import scala.concurrent.duration._
import scala.language.postfixOps

object ChangeAddressWorkerExecutorSpec {
  val clusterConfig = ConfigFactory.parseString("""
    akka {
      persistence {
        journal.plugin = "akka.persistence.journal.inmem"
        snapshot-store {
          plugin = "akka.persistence.snapshot-store.local"
          local.dir = "target/test-snapshots"
        }
      }
    }
    """).withFallback(ConfigFactory.load())
}


class ChangeAddressWorkerExecutorSpec(_system: ActorSystem) extends TestKit(_system)
  with ScalaFutures
  with Matchers
  with Inside
  with FlatSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender {

  def this() = this(ActorSystem("ChangeAddressWorkerExecutorSpec", ChangeAddressWorkerExecutorSpec.clusterConfig))
  val userId = 0L
  val quoteId = 11L
  val address = Address("26", "Highgate Street", "Forest", "London", "E17 6FD", Some(0L))
  implicit def timeout: Timeout = 1 second

  override def beforeAll(): Unit = {
  }

  override def afterAll: Unit = {
    shutdown(system)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
  }

  val modelActor = TestProbe()
  def changeAddressWorkerExecutorProps(worker: ActorRef, comId: String) = Props(new ChangeAddressWorkerExecutor(worker, comId) {
    override val userRepo: ActorRef = modelActor.ref
    override val insuranceService: ActorRef = modelActor.ref
  })

  val quoteNotificator = TestProbe()
  DistributedPubSub(system).mediator ! Subscribe(QuoteNotificator.ResultsTopic, quoteNotificator.ref)
  expectMsgType[SubscribeAck]
  DistributedPubSub(system).mediator ! GetTopics
  expectMsgType[CurrentTopics](10.seconds).getTopics() should contain(QuoteNotificator.ResultsTopic)

  "A ChangeAddressWorkerExecutor Actor" should "change the address" in {
    val worker = TestProbe()
    val comId = "comId1"
    val fsmRef: ActorRef = system.actorOf(changeAddressWorkerExecutorProps(worker.ref, comId))
    watch(fsmRef)
    val changeAddressCommand = ChangeAddress(comId, userId, address)
    fsmRef ! changeAddressCommand
    modelActor.expectMsg(changeAddressCommand)
    val changeResult = ChangeAddressResult(s"Address updated for User $userId")
    fsmRef ! changeResult
    worker.expectMsg(ChangeAddressAccepted(comId, changeResult))
    modelActor.expectMsg(QuoteInsurance(comId, userId))
    fsmRef ! QuoteInsuranceCreated(comId, quoteId)
    quoteNotificator.expectMsgType[QuoteInsuranceCreated](20.seconds).quoteId should be(quoteId)
    worker.expectMsg(10.seconds, ChangeAddressEnd(comId))
  }

  "A ChangeAddressWorkerExecutor Actor" should "change the address with error" in {
    val worker = TestProbe()
    val comId = "comId2"
    val fsmRef: ActorRef = system.actorOf(changeAddressWorkerExecutorProps(worker.ref, comId))
    watch(fsmRef)
    val changeAddressCommand = ChangeAddress(comId, userId, address)
    fsmRef ! changeAddressCommand
    modelActor.expectMsg(changeAddressCommand)
    val failure = Status.Failure(DataAccessException())
    fsmRef ! failure
    worker.expectMsg(failure)
    worker.expectMsg(10.seconds, ChangeAddressEnd(comId))
  }

  "A ChangeAddressWorkerExecutor Actor" should "change the address with quoting error" in {
    val worker = TestProbe()
    val comId = "comId3"
    val fsmRef: ActorRef = system.actorOf(changeAddressWorkerExecutorProps(worker.ref, comId))
    watch(fsmRef)
    val changeAddressCommand = ChangeAddress(comId, userId, address)
    fsmRef ! changeAddressCommand
    modelActor.expectMsg(changeAddressCommand)
    val changeResult = ChangeAddressResult(s"Address updated for User $userId")
    fsmRef ! changeResult
    worker.expectMsg(ChangeAddressAccepted(comId, changeResult))
    modelActor.expectMsg(QuoteInsurance(comId, userId))
    val failure = Status.Failure(DataAccessException())
    fsmRef ! failure
    quoteNotificator.expectNoMessage(10.seconds)
  }

}