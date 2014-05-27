package com.blinkbox.books.search

import akka.util.Timeout
import com.blinkbox.books.search.SearchService._
import com.blinkbox.books.spray.JsonFormats._
import com.blinkbox.books.spray.Directives
import com.blinkbox.books.spray.Paging._
import com.blinkbox.books.spray.Version1JsonSupport
import org.json4s.jackson.Serialization
import org.json4s.NoTypeHints
import org.json4s.Serialization
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.StatusCodes._
import spray.httpx.Json4sJacksonSupport
import spray.httpx.marshalling.Marshaller
import spray.routing.{ ExceptionHandler, HttpService, Route }
import spray.util.LoggingContext
import spray.http.HttpHeaders.RawHeader

/**
 * API for search service, expressed as Spray routes.
 */
trait SearchApi extends HttpService with Version1JsonSupport with Directives {

  import SearchApi._

  // Abstract definitions, to be provided by concrete implementations.
  val baseUrl: String
  val searchTimeout: Int
  def service: SearchService
  val corsOrigin: String
  val searchMaxAge: Duration
  val autoCompleteMaxAge: Duration

  implicit val timeout = Timeout(searchTimeout.seconds)
  implicit override def version1JsonFormats = blinkboxFormat(EntityTypeHints)

  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: IllegalArgumentException =>
        requestUri { uri => complete(BadRequest, s"Invalid request: ${e.getMessage}") }
    }

  /**
   * The overall route for the service.
   */
  lazy val route = respondWithSingletonHeader(RawHeader("Access-Control-Allow-Origin", corsOrigin)) {
    get {
      pathPrefix("search") {
        searchForBooks ~ similarBooks ~ searchSuggestions
      }
    }
  }

  /**
   * Route for book search requests.
   */
  lazy val searchForBooks =
    pathSuffix("books") {
      paged(defaultCount = 50) { page =>
        ordered() { sortOrder =>
          parameters('q) { query =>
            val result = service.search(query, page.offset, page.count, sortOrder)
            onSuccess(result) { result =>
              cacheable(searchMaxAge, QuerySearchResult(query, result.numberOfResults, result.suggestions, result.books,
                links(Some(result.numberOfResults.toInt), page.offset, page.count, s"$baseUrl/books")))
            }
          }
        }
      }
    }

  /**
   * Route for search for similar books.
   */
  lazy val similarBooks =
    path("books" / Isbn / "similar") { id =>
      paged(defaultCount = 10) { page =>
        val result = service.findSimilar(id, page.offset, page.count)
        onSuccess(result) { result =>
          cacheable(searchMaxAge, SimilarBooksSearchResult(id, result.numberOfResults, Seq(), result.books,
            links(Some(result.numberOfResults.toInt), page.offset, page.count, s"$baseUrl/books/$id/similar")))
        }
      }
    }

  /**
   * Route for suggestions, i.e. auto-completion as you type in the search field.
   */
  lazy val searchSuggestions =
    path("suggestions") {
      paged(defaultCount = 10) { page =>
        parameters('q) { query =>
          val result = service.suggestions(query, page.offset, page.count)
          implicit val typedJacksonFormats = blinkboxFormat()
          onSuccess(result) { suggestions =>
            cacheable(autoCompleteMaxAge, SuggestionsResult(suggestions))
          }
        }
      }
    }

  /**
   * Custom directive for specifying sort order.
   */
  def ordered(defaultOrder: SortOrder = SortOrder("RELEVANCE", desc = true)) =
    parameters('order ? defaultOrder.field, 'desc.as[Boolean] ? defaultOrder.desc).as(SortOrder)

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

