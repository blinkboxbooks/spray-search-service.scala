package com.blinkboxbooks.searchservice

import akka.util.Timeout
import org.json4s.jackson.Serialization
import org.json4s.NoTypeHints
import scala.concurrent.duration._
import scala.concurrent.{ Future, ExecutionContext }
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.httpx.Json4sJacksonSupport
import spray.routing.HttpService
import spray.routing.HttpServiceActor
import com.blinkboxbooks.common.spray.BlinkboxHelpers

/**
 * API for search service, expressed as Spray routes.
 */
trait SearchApi extends HttpService with Json4sJacksonSupport with BlinkboxHelpers {

  val model: SearchModel

  implicit val timeout = Timeout(5 seconds)
  implicit val json4sJacksonFormats = Serialization.formats(NoTypeHints).withBigDecimal

  val route =
    handleRejections(invalidParamHandler) {
      get {
        pathPrefix("search" / "books") {
          pathEnd {
            parameter('q) { query =>
              complete(StatusCodes.OK, s"Search query: $query")
            }
          } ~
            path(IntNumber / "similar") { id =>
              complete(s"Search for books similar to book with ID $id")
            }
        } ~
          path("search" / "suggestions") {
            parameter('q) { query =>
              complete(StatusCodes.OK, s"Suggestions query: $query")
            }
          }
      }
    }

}

/**
 * Actor implementing a search service that delegates requests to a given model.
 */
class SearchService(override val model: SearchModel) extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}
