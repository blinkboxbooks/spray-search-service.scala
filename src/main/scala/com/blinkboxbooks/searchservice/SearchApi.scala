package com.blinkboxbooks.searchservice

import akka.util.Timeout
import com.blinkboxbooks.common.spray.BlinkboxService
import com.blinkboxbooks.common.spray.BlinkboxService._
import com.blinkboxbooks.common.Utils._
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.StatusCodes._
import spray.httpx.Json4sJacksonSupport
import spray.routing.{ ExceptionHandler, HttpService, HttpServiceActor, Route }
import spray.util.LoggingContext

/**
 * API for search service, expressed as Spray routes.
 */
trait SearchApi extends HttpService with Json4sJacksonSupport with BlinkboxService {

  import SearchApi._

  // Abstract definitions, to be provided by concrete implementations.
  val baseUrl: String
  val searchTimeout: Int
  def service: SearchService

  implicit val timeout = Timeout(searchTimeout seconds)
  implicit def json4sJacksonFormats = typedBlinkboxFormat(EntityTypeHints).withBigDecimal

  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: IllegalArgumentException =>
        requestUri { uri =>
          log.warning("Request to {} could not be handled normally", uri)
          complete(BadRequest, s"Invalid request: ${e.getMessage}")
        }
    }

  /**
   * The overall route for the service.
   */
  def route = standardResponseHeaders {
    get {
      pathPrefix("search") {
        searchForBooks ~ similarBooks ~ searchSuggestions
      }
    }
  }

  /**
   * Route for book search requests.
   */
  def searchForBooks =
    pathSuffix("books") {
      paged(defaultCount = 50) { page =>
        ordered() { sortOrder =>
          parameters('q) { query =>
            val result = service.search(query, page.offset, page.count, sortOrder)
            onSuccess(result) { result =>
              complete(QuerySearchResult(query, result.numberOfResults, result.suggestions, result.books,
                links(result.numberOfResults, page.offset, page.count, s"$baseUrl/books")))
            }
          }
        }
      }
    }

  /**
   * Route for search for similar books.
   */
  def similarBooks =
    path("books" / Isbn / "similar") { id =>
      paged(defaultCount = 10) { page =>
        val result = service.findSimilar(id, page.offset, page.count)
        onSuccess(result) { result =>
          complete(SimilarBooksSearchResult(id, result.numberOfResults, Seq(), result.books,
            links(result.numberOfResults, page.offset, page.count, s"$baseUrl/books/$id/similar")))
        }
      }
    }

  /**
   * Route for suggestions, i.e. auto-completion as you type in the search field.
   */
  def searchSuggestions =
    path("suggestions") {
      paged(defaultCount = 10) { page =>
        parameters('q) { query =>
          val result = service.suggestions(query, page.offset, page.count)
          implicit val typedJacksonFormats = Serialization.formats(NoTypeHints).withBigDecimal
          onSuccess(result) { suggestions =>
            complete(SuggestionsResult(suggestions))
          }
        }
      }
    }

}

object SearchApi {

  // Value classes for responses.

  case class SuggestionsResult(
    items: Seq[Entity])

  case class QuerySearchResult(
    id: String,
    numberOfResults: Long,
    suggestions: Seq[String],
    books: Seq[Book],
    links: Seq[PageLink])

  case class SimilarBooksSearchResult(
    id: String,
    numberOfResults: Long,
    suggestions: Seq[String],
    books: Seq[Book],
    links: Seq[PageLink])

  /**
   * Custom type hints for JSON formats, helps with de-serialisation especially in
   * polymorphic (mixed) lists.
   */
  val EntityTypeHints = ExplicitTypeHints(Map(
    classOf[AuthorSuggestion] -> "urn:blinkboxbooks:schema:suggestion:contributor",
    classOf[BookSuggestion] -> "urn:blinkboxbooks:schema:suggestion:book",
    classOf[SuggestionsResult] -> "urn:blinkboxbooks:schema:list",
    classOf[QuerySearchResult] -> "urn:blinkboxbooks:schema:search",
    classOf[SimilarBooksSearchResult] -> "urn:blinkboxbooks:schema:search:similar"))

}

