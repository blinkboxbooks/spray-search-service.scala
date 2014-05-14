package com.blinkboxbooks.common.spray

import spray.http.HttpEntity
import spray.http.MediaType
import spray.http.MediaTypes
import spray.http.StatusCodes.BadRequest
import spray.routing.HttpService
import spray.routing.MalformedQueryParamRejection
import spray.routing.MissingQueryParamRejection
import spray.routing.RejectionHandler
import org.json4s.TypeHints
import org.json4s.FieldSerializer
import org.json4s.Formats
import org.json4s.DateFormat
import org.json4s.Serializer
import java.lang.reflect.Type
import org.json4s.NoTypeHints
import org.json4s.DefaultFormats

trait BlinkboxService {

  this: HttpService =>

  import BlinkboxService._

  // Request directives.

  /**
   * Common media type.
   */
  val `application/vnd.blinkboxbooks.data.v1+json` = MediaTypes.register(MediaType.custom(
    mainType = "application",
    subType = "vnd.blinkboxbooks.data.v1+json",
    binary = true, // binary as the encoding is defined as utf-8 by the json spec
    compressible = true))

  //
  // Response directives.
  //

  /**
   * Directive that removes the character set encoding from content types, typically "; UTF-8".
   */
  def removeCharsetEncoding(entity: HttpEntity) = entity.flatMap(e => HttpEntity(e.contentType.withoutDefinedCharset, e.data))

  /**
   * Combination of all response directives that are usually applied to service endpoints.
   */
  val standardResponseHeaders = mapHttpResponseEntity(removeCharsetEncoding) &
    respondWithMediaType(`application/vnd.blinkboxbooks.data.v1+json`)

  /**
   * Custom directive for extracting and validating page parameters (offset and count).
   */
  def paged(defaultCount: Int) = parameters('offset.as[Int] ? 0, 'count.as[Int] ? defaultCount).as(Page)

  /**
   * Custom directive for specifying sort order.
   */
  def ordered(defaultOrder: SortOrder = SortOrder("RELEVANCE", desc = true)) =
    parameters('order ? defaultOrder.field, 'desc.as[Boolean] ? defaultOrder.desc).as(SortOrder)

}

object BlinkboxService {

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

  case class Page(offset: Int, count: Int) {
    require(offset >= 0, "Offset must be 0 or greater")
    require(count > 0, "Count must be greater than 0")
  }
  case class PageLink(rel: String, href: String)

  /**
   * Value class for sorting parameters.
   */
  case class SortOrder(field: String, desc: Boolean)

  /**
   * Class that allows custom strings to be used as type hints for classes.
   */
  case class ExplicitTypeHints(customHints: Map[Class[_], String]) extends TypeHints {
    private val hintToClass = customHints.map(_.swap)
    override val hints = customHints.keys.toList
    override def hintFor(clazz: Class[_]) = customHints.get(clazz).get
    override def classFor(hint: String) = hintToClass.get(hint)
  }

  /**
   * JSON format that uses the names the type hint field as "type".
   */
  def typedBlinkboxFormat(hints: TypeHints = NoTypeHints): Formats = new DefaultFormats {
    override val typeHints: TypeHints = hints
    override val typeHintFieldName: String = "type"
  }

}
