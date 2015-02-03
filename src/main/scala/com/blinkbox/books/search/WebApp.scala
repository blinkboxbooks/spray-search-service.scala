package com.blinkbox.books.search

import akka.actor.{ Actor, Props, ActorSystem }
import akka.io.IO
import com.blinkbox.books.config._
import com.blinkbox.books.logging.Loggers
import com.typesafe.scalalogging.StrictLogging
import org.apache.solr.client.solrj.impl.{ HttpSolrServer, XMLResponseParser }
import scala.concurrent.duration._
import spray.can.Http
import spray.routing._

trait Core {
  implicit def system: ActorSystem
}

trait BootedCore extends Core with Configuration {
  implicit lazy val system = ActorSystem("akka-spray", config)
  sys.addShutdownHook(system.shutdown())
}

trait ConfiguredCore extends Core with Configuration

/**
 * A trait that contains the bulk of the start-up code for the service.
 */
trait WebApi extends RouteConcatenation with StrictLogging {
  this: ConfiguredCore =>

  logger.info("Starting search service")

  val appConfig = AppConfig(config.getConfig("service.search"))

  logger.info(s"Configured URL for Solr: ${appConfig.solr.url}")
  val solrServer = new HttpSolrServer(appConfig.solr.url)
  solrServer.setParser(new XMLResponseParser())

  val service = new SolrSearchService(appConfig.query, solrServer)

  val webService = system.actorOf(Props(
    new SearchWebService(service, appConfig.api)), "search-service")

  logger.info("Started search service")
}

/**
 * Actor implementing a search web service that delegates requests to a given implementation.
 */
class SearchWebService(override val service: SearchService, override val apiConfig: ApiConfig)
  extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}

/**
 * The application that ties everything together and gets run on startup.
 */
object WebApp extends App with BootedCore with ConfiguredCore with WebApi with Configuration with Loggers with StrictLogging {

  IO(Http)(system) ! Http.Bind(webService, appConfig.api.hostname, appConfig.api.port)

}
