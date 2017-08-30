package com.example.hellostream.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.hellostream.api.HellolagomStreamService
import com.example.hello.api.HellolagomService
import com.softwaremill.macwire._

class HellolagomStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new HellolagomStreamApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HellolagomStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[HellolagomStreamService])
}

abstract class HellolagomStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[HellolagomStreamService](wire[HellolagomStreamServiceImpl])

  // Bind the HellolagomService client
  lazy val hellolagomService = serviceClient.implement[HellolagomService]
}
