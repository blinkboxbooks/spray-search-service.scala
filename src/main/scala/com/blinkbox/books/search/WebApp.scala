package com.blinkbox.books.search

import akka.actor.{ Actor, Props, ActorSystem }
import akka.io.IO
import org.apache.solr.client.solrj.impl.HttpSolrServer
import org.apache.solr.client.solrj.impl.XMLResponseParser
import spray.can.Http
import spray.routing._

trait Core {
  implicit def system: ActorSystem
}

trait BootedCore extends Core {
  implicit lazy val system = ActorSystem("akka-spray")
  sys.addShutdownHook(system.shutdown())
}

/**
 * A trait that contains the bulk of the start-up code for the service.
 */
trait WebApi extends RouteConcatenation {
  this: Core =>

  val solrServer = new HttpSolrServer("http://localhost:8983/solr/books") // TODO: get from config.
  //    val solrServer = new HttpSolrServer("http://solr.mobcastdev.com/solr/books") // TODO: get from config.
  solrServer.setParser(new XMLResponseParser())

  val model = new SolrSearchService(solrServer)

  val baseUrl = "search" // TODO: Get from config.
  val searchTimeout = 5 // TODO: Get from config.
  val service = system.actorOf(Props(new SearchWebService(model, baseUrl, searchTimeout)), "search-service")
}

/**
 * Actor implementing a search service that delegates requests to a given model.
 */
class SearchWebService(override val service: SearchService, override val baseUrl: String,
  override val searchTimeout: Int)
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
