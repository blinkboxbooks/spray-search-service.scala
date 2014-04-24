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

/**
 * API for search service, expressed as Spray routes.
 */
trait SearchApi extends HttpService with Json4sJacksonSupport with BlinkboxHelpers {

  // Abstract values, to be provided by concrete implementations.
  val model: SearchModel
  val defaultCount: Int

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

  val route = handleRejections(invalidParamHandler) {
    addBBBMediaTypeToResponse {
      get {
        pathPrefix("search" / "books") {
          pathEnd {
            parameters('q, 'offset.as[Int] ? 0, 'count.as[Int] ? defaultCount, 'order ?, 'desc.as[Boolean] ? true) {
              (query, offset, count, order, desc) =>
                val result = model.search(query, offset, count, order, desc)
                onSuccess(result) { foundBooks =>
                  complete(SearchResult("urn:blinkboxbooks:schema:search",
                    query, foundBooks.size, foundBooks,
                    links(foundBooks, foundBooks.size, offset, count, "search/books")))
                }
            }
          } ~
            path(Segment / "similar") { id =>
              parameters('offset.as[Int] ? 0, 'count.as[Int] ? defaultCount) {
                (offset, count) =>
                  val result = model.findSimilar(id, offset, count)
                  onSuccess(result) { foundBooks =>
                    complete(SearchResult("urn:blinkboxbooks:schema:search:similar",
                      id, foundBooks.size, foundBooks,
                      links(foundBooks, foundBooks.size, offset, count, s"search/books/$id/similar")))
                  }
              }
            }
        } ~
          path("search" / "suggestions") {
            parameters('q, 'offset.as[Int] ? 0, 'count.as[Int] ? defaultCount) {
              (query, offset, count) =>
                val result = model.suggestions(query, offset, count)
                onSuccess(result) { foundBooks =>
                  complete(SuggestionsResult("urn:blinkboxbooks:schema:list", foundBooks))
                }
            }
          }
      }
    }
  }

  // TODO: Could go somewhere common?
  def links(items: Seq[Book], numberOfResults: Int, offset: Int, count: Int, baseUrl: String) = {

    val hasMore = numberOfResults > offset + count

    val thisPageLink = s"$baseUrl?count=$count&offset=$offset"
    val thisPage = Some(PageLink("this", thisPageLink))

    val previousPage = if (offset > 0) {
      val link = s"$baseUrl?count=$count&offset=${(offset - count).max(0)}"
      Some(PageLink("previous", link))
    } else None

    val nextPage = if (hasMore) {
      val link = s"$baseUrl?count=$count&offset=${offset + count}"
      Some(PageLink("next", link))
    } else None

    List(thisPage, previousPage, nextPage).flatten
  }

}

/**
 * Actor implementing a search service that delegates requests to a given model.
 */
class SearchService(override val model: SearchModel, override val defaultCount: Int) extends HttpServiceActor with SearchApi {

  def receive = runRoute(route)

}
