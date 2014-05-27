package com.blinkbox.books.search

import akka.actor.{ Actor, Props, ActorSystem }
import akka.io.IO
import org.apache.solr.client.solrj.impl.HttpSolrServer
import org.apache.solr.client.solrj.impl.XMLResponseParser
import scala.concurrent.duration._
import spray.can.Http
import spray.routing._
import com.typesafe.scalalogging.slf4j.Logging
import com.blinkbox.books.config.Configuration

trait Core {
  implicit def system: ActorSystem
}

trait BootedCore extends Core {
  implicit lazy val system = ActorSystem("akka-spray")
  sys.addShutdownHook(system.shutdown())
}

trait ConfiguredCore extends Core with Configuration

/**
 * A trait that contains the bulk of the start-up code for the service.
 */
trait WebApi extends RouteConcatenation with Logging {
  this: ConfiguredCore =>

  logger.info("Starting service")

  // The config property names used here are those of the previous search service.
  // Going forward, it will be better to move these to a proper hierarchy, using a well defined
  // name scheme across services etc.
  val solrHostname = config.getString("solr.hostname")
  val solrPort = config.getInt("solr.port")
  val solrRootPath = config.getString("solr.root.path")
  val solrBookIndex = config.getString("solr.index.books")
  val solrUrl = s"http://$solrHostname:$solrPort$solrRootPath/$solrBookIndex"
  logger.info(s"Configured URL for Solr: $solrUrl")

  val solrServer = new HttpSolrServer(solrUrl)
  solrServer.setParser(new XMLResponseParser())

  val freeQueries = config.getString("search.free.queries").split(",").map(_.toLowerCase.trim)
  val nameBoost = config.getDouble("search.name.boost")
  val contentBoost = config.getDouble("search.content.boost")
  val exactAuthorBoost = config.getDouble("search.exact.author.boost")
  val exactTitleBoost = config.getDouble("search.exact.title.boost")
  val searchConfig = new SolrSearchConfig(freeQueries, nameBoost, contentBoost, exactAuthorBoost, exactTitleBoost)
  logger.info(s"Search parameter configuration: $searchConfig")

  val service = new SolrSearchService(searchConfig, solrServer)

  val baseUrl = config.getString("search.path")
  val searchTimeout = config.getInt("search.timeout")
  val corsOrigin = config.getString("http.cors.origin")
  val searchMaxAge = config.getInt("search.maxAgeSeconds").seconds
  val autoCompleteMaxAge = config.getInt("autocomplete.maxAgeSeconds").seconds
  
  val webService = system.actorOf(Props(
    new SearchWebService(service, baseUrl, searchTimeout, corsOrigin, searchMaxAge, autoCompleteMaxAge)), "search-service")

  logger.info("Started web service")
}

/**
 * Actor implementing a search service that delegates requests to a given model.
 */
class SearchWebService(override val service: SearchService, override val baseUrl: String,
  override val searchTimeout: Int, override val corsOrigin: String,
  override val searchMaxAge: Duration, override val autoCompleteMaxAge: Duration)
  extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}

/**
 * The application that ties everything together and gets run on startup.
 */
object WebApp extends App with BootedCore with ConfiguredCore with WebApi with Configuration {

  IO(Http)(system) ! Http.Bind(webService, "0.0.0.0", port = config.getInt("search.port"))

}
