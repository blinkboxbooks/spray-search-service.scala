package com.blinkboxbooks.searchservice

import akka.util.Timeout
import org.json4s.jackson.Serialization
import org.json4s.NoTypeHints
import scala.concurrent.duration._
import scala.concurrent.{ Future, ExecutionContext }
import ExecutionContext.Implicits.global
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.httpx.Json4sJacksonSupport
import spray.routing.HttpService
import spray.routing.HttpServiceActor
import com.blinkboxbooks.common.spray.BlinkboxHelpers
import spray.routing.Route

/**
 * Abstract defintions of REST API for search service.
 */
trait SearchRoutes {

  def searchForBooks: Route

  def similarBooks: Route

  def searchSuggestions: Route

}

/**
 * API for search service, expressed as Spray routes.
 */
trait SearchApi extends HttpService with SearchRoutes with Json4sJacksonSupport with BlinkboxHelpers {

  // Abstract values, to be provided by concrete implementations.
  val model: SearchModel
  val baseUrl: String

  implicit val timeout = Timeout(5 seconds)
  implicit val json4sJacksonFormats = Serialization.formats(NoTypeHints).withBigDecimal

  case class SuggestionsResult(
    `type`: String,
    items: List[Entity])

  case class SearchResult(
    `type`: String,
    id: String,
    numberOfResults: Int,
    books: List[Book],
    links: Seq[PageLink])

  override val searchForBooks =
    pathSuffix("books") {
      paged(defaultCount = 50) { page =>
        parameters('q, 'order ?, 'desc.as[Boolean] ? true) { (query, order, desc) =>
          val result = model.search(query, page.offset, page.count, order, desc)
          onSuccess(result) { foundBooks =>
            complete(SearchResult("urn:blinkboxbooks:schema:search",
              query, foundBooks.size, foundBooks,
              links(foundBooks.size, page.offset, page.count, s"$baseUrl/books")))
          }
        }
      }
    }

  override val similarBooks =
    path("books" / Segment / "similar") { id =>
      paged(defaultCount = 10) { page =>
        val result = model.findSimilar(id, page.offset, page.count)
        onSuccess(result) { foundBooks =>
          complete(SearchResult("urn:blinkboxbooks:schema:search:similar",
            id, foundBooks.size, foundBooks,
            links(foundBooks.size, page.offset, page.count, s"$baseUrl/books/$id/similar")))
        }
      }
    }

  override val searchSuggestions =
    path("suggestions") {
      paged(defaultCount = 50) { page =>
        parameters('q) { query =>
          val result = model.suggestions(query, page.offset, page.count)
          onSuccess(result) { foundBooks =>
            complete(SuggestionsResult("urn:blinkboxbooks:schema:list", foundBooks))
          }
        }
      }
    }

  val route = handleRejections(invalidParamHandler) {
    addBBBMediaTypeToResponse {
      get {
        pathPrefix("search") {
          searchForBooks ~ similarBooks ~ searchSuggestions
        }
      }
    }
  }

}

/**
 * Actor implementing a search service that delegates requests to a given model.
 */
class SearchService(override val model: SearchModel, override val baseUrl: String)
  extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}
