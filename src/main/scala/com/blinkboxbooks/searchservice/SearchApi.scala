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

  // Abstract values, to be provided by concrete implementations.
  val model: SearchModel
  val defaultCount: Int

  implicit val timeout = Timeout(5 seconds)
  implicit val json4sJacksonFormats = Serialization.formats(NoTypeHints).withBigDecimal

  val route = handleRejections(invalidParamHandler) {
    addBBBMediaTypeToResponse {
      get {
        pathPrefix("search" / "books") {
          pathEnd {
            parameters('q, 'offset.as[Int] ? 0, 'count.as[Int] ? defaultCount, 'order ?, 'desc.as[Boolean] ? true) {
              (query, offset, count, order, desc) =>
                complete(StatusCodes.OK, s"Search query: $query, offset=$offset, count=$count, order=$order, desc=$desc")
            }
          } ~
            path(IntNumber / "similar") { id =>
              parameters('offset.as[Int] ? 0, 'count.as[Int] ? defaultCount) {
                (offset, count) =>
                  complete(s"Search for books similar to book with ID $id, offset=$offset, count=$count")
              }
            }
        } ~
          path("search" / "suggestions") {
            parameters('q, 'offset.as[Int] ? 0, 'count.as[Int] ? defaultCount) {
              (query, offset, count) =>
                complete(StatusCodes.OK, s"Suggestions query: $query, offset=$offset, count=$count")
            }
          }
      }
    }
  }

}

/**
 * Actor implementing a search service that delegates requests to a given model.
 */
class SearchService(override val model: SearchModel, override val defaultCount: Int) extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}
