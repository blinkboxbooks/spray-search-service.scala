package com.blinkboxbooks.searchservice

import akka.actor.Props
import akka.io.IO
import spray.can.Http
import spray.routing._

trait Api extends RouteConcatenation {
  this: Core =>

  val model = new SolrSearchModel()
  val baseUrl = "search" // TODO: Get from config.
  val service = system.actorOf(Props(new SearchService(model, baseUrl)), "search-service")
}

trait Web {
  this: Api with Core =>
  IO(Http)(system) ! Http.Bind(service, "0.0.0.0", port = 8080)
}

object WebApp extends App with BootedCore with Core with Api with Web

