package com.blinkboxbooks.common.spray

import spray.routing.RejectionHandler
import spray.routing.HttpService
import spray.routing.MissingQueryParamRejection
import spray.routing.MalformedQueryParamRejection
import spray.http.HttpEntity
import spray.http.MediaTypes
import spray.http.MediaType
import spray.http.StatusCodes.BadRequest
import spray.routing.Rejection
import spray.routing.Route

trait BlinkboxHelpers {

  this: HttpService =>

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

  // Response directives.

  def removeCharsetEncoding(entity: HttpEntity) = entity.flatMap(e => HttpEntity(e.contentType.withoutDefinedCharset, e.data))

  val addBBBMediaTypeToResponse = mapHttpResponseEntity(removeCharsetEncoding) &
    respondWithMediaType(`application/vnd.blinkboxbooks.data.v1+json`)

  // Routing directives.

  /** Directive that extracts standard 'offset' and 'count' parameters. */
  def paged(defaultCount: Int) = parameters('offset.as[Int] ? 0, 'count.as[Int] ? defaultCount)

  /** Directive that validates standard 'offset' and 'count' parameters. */
  def validateOffsetAndCount(offset: Int, count: Int) =
    validate(offset >= 0, "Offset must be 0 or greater") &
      validate(count > 0, "Count must be greater than 0")

  // Case classes for response schema.

  case class PageLink(rel: String, href: String)

  /**
   * Generate links for use in paged results.
   */
  def links(numberOfResults: Int, offset: Int, count: Int, linkBaseUrl: String) = {

    val hasMore = numberOfResults > offset + count

    val thisPageLink = s"$linkBaseUrl?count=$count&offset=$offset"
    val thisPage = Some(PageLink("this", thisPageLink))

    val previousPage = if (offset > 0) {
      val link = s"$linkBaseUrl?count=$count&offset=${(offset - count).max(0)}"
      Some(PageLink("previous", link))
    } else None

    val nextPage = if (hasMore) {
      val link = s"$linkBaseUrl?count=$count&offset=${offset + count}"
      Some(PageLink("next", link))
    } else None

    List(thisPage, previousPage, nextPage).flatten
  }

}

