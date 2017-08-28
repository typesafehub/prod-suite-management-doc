package com.lightbend.lagom.impl

import com.lightbend.external.api.{LagomService, LibertyService}
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.dns.DnsServiceLocatorComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServer}
import com.softwaremill.macwire.wire
import play.api.libs.ws.ahc.AhcWSComponents

// #application-loader
class LagomLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext) = new MyLagomApplication(context) with KubernetesRuntimeComponents

  override def loadDevMode(context: LagomApplicationContext) = new MyLagomApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[LibertyService])
}

sealed trait KubernetesRuntimeComponents extends LagomServiceClientComponents with DnsServiceLocatorComponents

abstract class MyLagomApplication(context: LagomApplicationContext) extends LagomApplication(context)
  with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[LagomService](wire[LagomServiceImpl])
  lazy val libertyService = serviceClient.implement[LibertyService]

}

// #application-loader
