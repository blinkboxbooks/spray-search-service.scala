package com.blinkboxbooks.common.spray

import spray.http.HttpEntity
import spray.http.MediaType
import spray.http.MediaTypes
import spray.http.StatusCodes.BadRequest
import spray.routing.HttpService
import spray.routing.MalformedQueryParamRejection
import spray.routing.MissingQueryParamRejection
import spray.routing.RejectionHandler

trait BlinkboxHelpers {

  this: HttpService =>

  import BlinkboxHelpers._

  // Request directives.

  /**
   * Common media type.
   */
  val `application/vnd.blinkboxbooks.data.v1+json` = MediaTypes.register(MediaType.custom(
    mainType = "application",
    subType = "vnd.blinkboxbooks.data.v1+json",
    binary = true, // binary as the encoding is defined as utf-8 by the json spec
    compressible = true))

  val invalidParamHandler = RejectionHandler {
    case MalformedQueryParamRejection(paramName, _, _) :: _ =>
      complete(BadRequest, s"Invalid value for $paramName parameter")
    case MissingQueryParamRejection(paramName) :: _ =>
      complete(BadRequest, s"Missing value for $paramName parameter")
  }

  /** Matcher for ISBN. */
  //TODO: Add ^ and $, no?!
  val Isbn = """\d{13}""".r

  // Response directives.

  def removeCharsetEncoding(entity: HttpEntity) = entity.flatMap(e => HttpEntity(e.contentType.withoutDefinedCharset, e.data))

  val standardResponseHeaders = mapHttpResponseEntity(removeCharsetEncoding) &
    respondWithMediaType(`application/vnd.blinkboxbooks.data.v1+json`)

  /** Custom directive for extracting and validating page parameters (offset and count). */
  def paged(defaultCount: Int) = parameters('offset.as[Int] ? 0, 'count.as[Int] ? defaultCount).as(Page)

  /**
   * Generate links for use in paged results.
   */
  def links(numberOfResults: Long, offset: Long, count: Long, linkBaseUrl: String) = {

    val hasMore = numberOfResults > offset + count

    val thisPageLink = s"$linkBaseUrl?count=$count&offset=$offset"
    val thisPage = Some(PageLink("this", thisPageLink))

    val previousPage = if (offset > 0) {
      val link = s"$linkBaseUrl?count=$count&offset=${(offset - count).max(0)}"
      Some(PageLink("prev", link))
    } else None

    val nextPage = if (hasMore) {
      val link = s"$linkBaseUrl?count=$count&offset=${offset + count}"
      Some(PageLink("next", link))
    } else None

    List(thisPage, previousPage, nextPage).flatten
  }

}

object BlinkboxHelpers {

  case class Page(offset: Int, count: Int) {
    require(offset >= 0, "Offset must be 0 or greater")
    require(count > 0, "Count must be greater than 0")
  }
  case class PageLink(rel: String, href: String)

}
