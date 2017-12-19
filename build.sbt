lazy val akkaHttpVersion = "10.0.11"
lazy val akkaVersion    = "2.5.8"
lazy val cassandraPluginVersion = "0.59"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.dataspartan",
      scalaVersion    := "2.12.4"
    )),
    name := "akka-poc",
    version := "0.1",
    fork in Test := true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-cluster"         % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools"   % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence"     % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka" %% "akka-actor"           % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"           % akkaVersion,
      "com.typesafe.slick" %% "slick"               % "3.2.1",
      "com.h2database"    % "h2"                    % "1.4.196",
      "ch.qos.logback"    %  "logback-classic"      % "1.2.3",
      "com.typesafe.akka" %% "akka-stream-kafka" % "0.18",

      "com.typesafe.akka" %% "akka-persistence-cassandra"          % cassandraPluginVersion,
      // this allows us to start cassandra from the sample
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraPluginVersion,


      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.1"         % Test,
      "commons-io"        %  "commons-io"           % "2.4"           % Test
    )
  )