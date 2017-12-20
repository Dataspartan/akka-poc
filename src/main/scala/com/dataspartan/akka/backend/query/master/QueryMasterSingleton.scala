package com.dataspartan.akka.backend.query.master

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton._

object QueryMasterSingleton {

  private val singletonName = "query-master"
  private val singletonRole = "query-backend"

  // #singleton
  def startSingleton(system: ActorSystem) = {

    system.actorOf(
      ClusterSingletonManager.props(
        QueryMaster.props,
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
