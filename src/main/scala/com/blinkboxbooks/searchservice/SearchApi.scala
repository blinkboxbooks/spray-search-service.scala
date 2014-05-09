package com.blinkboxbooks.searchservice

import akka.util.Timeout
import com.blinkboxbooks.common.spray.BlinkboxHelpers
import com.blinkboxbooks.common.spray.BlinkboxHelpers._
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import spray.httpx.Json4sJacksonSupport
import spray.routing.HttpService
import spray.routing.HttpServiceActor
import spray.routing.Route

/**
 * Abstract defintions of REST API for search service.
 */
trait SearchRoutes extends HttpService {

  def searchForBooks: Route

  def similarBooks: Route

  def searchSuggestions: Route

}

/**
 * API for search service, expressed as Spray routes.
 */
trait SearchApi extends HttpService with SearchRoutes with Json4sJacksonSupport with BlinkboxHelpers {

  import SearchApi._

  // Abstract definitions, to be provided by concrete implementations.
  val baseUrl: String
  def service: SearchService

  implicit val timeout = Timeout(5 seconds)
  implicit val json4sJacksonFormats = Serialization.formats(NoTypeHints).withBigDecimal

  /**
   * The overall route for the service.
   */
  def route = handleRejections(invalidParamHandler) {
    standardResponseHeaders {
      get {
        pathPrefix("search") {
          searchForBooks ~ similarBooks ~ searchSuggestions
        }
      }
    }
  }

  override def searchForBooks =
    pathSuffix("books") {
      paged(defaultCount = 50) { page =>
        ordered() { sortOrder =>
          parameters('q) { query =>
            val result = service.search(query, page.offset, page.count, sortOrder)
            onSuccess(result) { result =>
              complete(SearchResult("urn:blinkboxbooks:schema:search",
                query, result.numberOfResults, result.suggestions, result.books,
                links(result.numberOfResults, page.offset, page.count, s"$baseUrl/books")))
            }
          }
        }
      }
    }

  override def similarBooks =
    path("books" / Isbn / "similar") { id =>
      paged(defaultCount = 10) { page =>
        val result = service.findSimilar(id, page.offset, page.count)
        onSuccess(result) { result =>
          complete(SearchResult("urn:blinkboxbooks:schema:search:similar",
            id, result.numberOfResults, Seq(), result.books,
            links(result.numberOfResults, page.offset, page.count, s"$baseUrl/books/$id/similar")))
        }
      }
    }

  override def searchSuggestions =
    path("suggestions") {
      paged(defaultCount = 10) { page =>
        parameters('q) { query =>
          val result = service.suggestions(query, page.offset, page.count)
          onSuccess(result) { suggestions =>
            complete(SuggestionsResult("urn:blinkboxbooks:schema:list", suggestions))
          }
        }
      }
    }

}

object SearchApi {

  // Value classes for responses.

  case class SuggestionsResult(
    `type`: String,
    items: Seq[Entity])

  case class SearchResult(
    `type`: String,
    id: String,
    numberOfResults: Long,
    suggestions: Seq[String],
    books: Seq[Book],
    links: Seq[PageLink])

}

