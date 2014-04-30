package com.blinkboxbooks.searchservice

import akka.actor.Props
import akka.io.IO
import spray.can.Http
import spray.routing._
import com.blinkboxbooks.common.spray.Core
import com.blinkboxbooks.common.spray.BootedCore

trait WebApi extends RouteConcatenation {
  this: Core =>

  val model = new SolrSearchModel()
  val baseUrl = "search" // TODO: Get from config.
  val service = system.actorOf(Props(new SearchService(model, baseUrl)), "search-service")
}

/**
 * Actor implementing a search service that delegates requests to a given model.
 * Includes a Swagger endpoint for the service.
 */
class SearchService(override val model: SearchModel, override val baseUrl: String)
  extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}

/**
 * The application that ties everything together and gets run on startup.
 */
object WebApp extends App with BootedCore with Core with WebApi {

  // TODO: Get port number from config.
  IO(Http)(system) ! Http.Bind(service, "0.0.0.0", port = 8080)

}

