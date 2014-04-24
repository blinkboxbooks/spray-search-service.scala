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

  def removeCharsetEncoding(entity: HttpEntity) = entity.flatMap(e => HttpEntity(e.contentType.withoutDefinedCharset, e.data))

  val addBBBMediaTypeToResponse = mapHttpResponseEntity(removeCharsetEncoding) &
    respondWithMediaType(`application/vnd.blinkboxbooks.data.v1+json`)

  def validateCountAndOffset(count: Int, offset: Int) = validate(count >= 1 && count < 100, "Count must be between 1 and 100") &
    validate(offset >= 0, "Offset must not be less than 0")

  // Case classes for response schema.

  case class PageLink(rel: String, href: String)

  /**
   * Generate links for use in
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

