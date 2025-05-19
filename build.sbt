name := "graphql-scala-sqlite"
version := "0.1.0"
scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "3.5.3",
  "org.sangria-graphql" %% "sangria-spray-json" % "1.0.2",
  "com.typesafe.akka" %% "akka-http" % "10.5.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0",
  "com.typesafe.akka" %% "akka-stream" % "2.8.0",
  "org.xerial" % "sqlite-jdbc" % "3.41.2.2",
  "org.slf4j" % "slf4j-simple" % "2.0.7"
)
