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

  /** TODO: Remove this debugging code. */
  val debuggerPf: PartialFunction[List[Rejection], Route] = { case value @ _ if debug(value) => ??? }
  def debug(value: Any) = {
    println("*** " + value)
    false
  }

  val invalidParamHandler1 = RejectionHandler {
    case MalformedQueryParamRejection(paramName, _, _) :: _ =>
      complete(BadRequest, s"Invalid value for $paramName parameter")
    case MissingQueryParamRejection(paramName) :: _ =>
      complete(BadRequest, s"Missing value for $paramName parameter")
  }
  val invalidParamHandler = debuggerPf orElse invalidParamHandler1

  def removeCharsetEncoding(entity: HttpEntity) = entity.flatMap(e => HttpEntity(e.contentType.withoutDefinedCharset, e.data))

  val addBBBMediaTypeToResponse = mapHttpResponseEntity(removeCharsetEncoding) &
    respondWithMediaType(`application/vnd.blinkboxbooks.data.v1+json`)

  def validateCountAndOffset(count: Int, offset: Int) = validate(count >= 1 && count < 100, "Count must be between 1 and 100") &
    validate(offset >= 0, "Offset must not be less than 0")

  // Case classes for response schema.
  case class ResultList[T](`type`: String, numberOfResults: Int, offset: Int, count: Int, items: List[T], links: Option[List[PageLink]])
  case class PageLink(rel: String, href: String)

  // assumes we requested count + 1 promotions from the database. We use this to work out if we are serving the last 'page'
  def toListResponse[T](items: List[T], offset: Int, count: Int, baseUrl: String): ResultList[T] = {

    val hasMore = items.size > count
    val numberOfResults = if (hasMore) (items.size - 1).max(0) else items.size

    val previousPage = if (offset > 0) {
      val link = s"$baseUrl?count=${count.min(offset)}&offset=${(offset - count).max(0)}"
      Some(PageLink("previous", link))
    } else None

    val nextPage = if (hasMore) {
      val link = s"$baseUrl?count=$count&offset=${offset + count}"
      Some(PageLink("next", link))
    } else None

    val links = List(previousPage, nextPage).flatten match {
      case List() => None
      case list => Some(list)
    }
    ResultList("urn:blinkboxbooks:schema:list", numberOfResults, offset, count, items.take(numberOfResults), links)
  }

}

