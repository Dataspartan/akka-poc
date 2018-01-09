package com.dataspartan.akka.backend.command.master

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton._

import scala.concurrent.duration._

object CommandMasterSingleton {

  private val singletonName = "command-master"
  private val singletonRole = "command-backend"

  // #singleton
  def startSingleton(system: ActorSystem) = {
    val workTimeout = system.settings.config.getDuration("distributed-workers.work-timeout").getSeconds.seconds

    system.actorOf(
      ClusterSingletonManager.props(
        CommandMaster.props(workTimeout),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole(singletonRole)
      ),
      singletonName)
  }
  // #singleton

  // #proxy
  def proxyProps(system: ActorSystem) = ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole(singletonRole),
    singletonManagerPath = s"/user/$singletonName")
  // #proxy
}
