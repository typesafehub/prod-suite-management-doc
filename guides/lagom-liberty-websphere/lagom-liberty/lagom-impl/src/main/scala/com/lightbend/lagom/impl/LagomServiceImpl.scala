package com.lightbend.lagom.impl

import akka.NotUsed
import com.lightbend.external.api.{LagomService, LibertyService}
import com.lightbend.lagom.scaladsl.api.{ServiceCall, ServiceLocator}

import scala.concurrent.{ExecutionContext, Future}


class LagomServiceImpl(externalService: LibertyService, serviceLocator: ServiceLocator)(implicit ec: ExecutionContext) extends LagomService {

  override def speak(): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    externalService.echo().invoke().map(result => s"External Service Replied with --- \n$result")
  }

  override def echo(id: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    Future.successful(s"Hello $id")
  }
}