package com.example.hellostream.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.example.hellostream.api.HellolagomStreamService
import com.example.hello.api.HellolagomService

import scala.concurrent.Future

/**
  * Implementation of the HellolagomStreamService.
  */
class HellolagomStreamServiceImpl(hellolagomService: HellolagomService) extends HellolagomStreamService {
  def stream = ServiceCall { hellos =>
    Future.successful(hellos.mapAsync(8)(hellolagomService.hello(_).invoke()))
  }
}
