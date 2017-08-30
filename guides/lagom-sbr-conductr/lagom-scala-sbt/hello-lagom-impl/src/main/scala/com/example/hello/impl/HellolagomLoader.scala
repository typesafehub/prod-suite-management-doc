package com.example.hello.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.hello.api.HellolagomService
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.softwaremill.macwire._
// #HellolagomLoader-import
import com.typesafe.conductr.bundlelib.lagom.scaladsl.ConductRApplicationComponents
// #HellolagomLoader-import

class HellolagomLoader extends LagomApplicationLoader {

  // #HellolagomLoader-load
  override def load(context: LagomApplicationContext): LagomApplication =
    new HellolagomApplication(context) with ConductRApplicationComponents
  // #HellolagomLoader-load

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HellolagomApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[HellolagomService])
}

abstract class HellolagomApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[HellolagomService](wire[HellolagomServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = HellolagomSerializerRegistry

  // Register the hello-lagom persistent entity
  persistentEntityRegistry.register(wire[HellolagomEntity])
}
