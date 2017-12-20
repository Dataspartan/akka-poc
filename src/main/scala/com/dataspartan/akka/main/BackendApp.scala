package com.dataspartan.akka.main

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.ActorSystem
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.dataspartan.akka.backend.query.master.QueryMasterSingleton
import com.dataspartan.akka.http.HttpRestServer
import com.typesafe.config.{Config, ConfigFactory}


object BackendApp {

  val backEndPortRange = 2550 to 2560
  val queryWorkerPortRange = 3000 to 3499
  val commandWorkerPortRange = 3500 to 3999

  def main(args: Array[String]): Unit = {

    args.toList match {

//      case Nil =>
//        startClusterInSameJvm()

      case portString::role::Nil if portString.matches("""\d+""") =>
        val port = portString.toInt
        if (backEndPortRange.contains(port)) startBackEnd(role, port)

      case portString::Nil if portString.matches("""\d+""") =>
        val port = portString.toInt
        if (queryWorkerPortRange.contains(port)) startQueryWorkerSystem(port)
      //        else startWorker(port, args.lift(1).map(_.toInt).getOrElse(1))

      case "rest"::Nil =>
        startFrontEnd()
        println("Started HTTP Rest Server")

      case "cassandra"::Nil =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

      case _ => println("Parameters not valid")
    }

  }

  def startClusterInSameJvm(): Unit = {
//    startCassandraDatabase()

//    // two backend nodes
//    startBackEnd(2551)
//    startBackEnd(2552)
//    // two front-end nodes
//    startFrontEnd(3000)
////    startFrontEnd(3001)
//    // two worker nodes with two worker actors each
//    startWorker(5001, 2)
//    startWorker(5002, 2)
  }


  def startBackEnd(role: String, port: Int): Unit = {
    val system = ActorSystem("ClusterSystem", config(port, role))
    QueryMasterSingleton.startSingleton(system)
  }

  def startFrontEnd(): Unit = {
    HttpRestServer.run()
  }

  def startQueryWorkerSystem(port: Int): Unit = {
    ActorSystem("ClusterSystem", config(port, "query-worker"))
  }

//  /**
//    * Start a worker node, with n actual workers that will accept and process workloads
//    */
//  // #worker
//  def startWorker(port: Int, workers: Int): Unit = {
//    val system = ActorSystem("ClusterSystem", config(port, "worker"))
//    val masterProxy = system.actorOf(
//      MasterSingleton.proxyProps(system),
//      name = "masterProxy")
//
//    (1 to workers).foreach(n =>
//      system.actorOf(Worker.props(masterProxy), s"worker-$n")
//    )
//  }
//  // #worker

  def config(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.port=$port
      akka.cluster.roles=[$role]
    """).withFallback(ConfigFactory.load())

  /**
    * To make the sample easier to run we kickstart a Cassandra instance to
    * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
    * in a real application a pre-existing Cassandra cluster should be used.
    */
  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource,
      clean = false,
      port = 9042
    )

    // shut the cassandra instance down when the JVM stops
    sys.addShutdownHook {
      CassandraLauncher.stop()
    }
  }

}
