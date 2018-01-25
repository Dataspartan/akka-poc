package com.dataspartan.akka.bakend

import com.typesafe.config.ConfigFactory

object UtilConfig {
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