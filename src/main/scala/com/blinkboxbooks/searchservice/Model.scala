package com.blinkboxbooks.searchservice

import scala.concurrent.Future
import com.blinkboxbooks.common.spray.BlinkboxHelpers._

/**
 * Domain classes for search results.
 *
 * Note that these currently include the 'type' field returned by the web API. This field should be removed
 * from these classes and added in the web layer at some point.
 */

sealed trait Entity {
  val `type`: Option[String]
  val id: String
  val title: String
}

case class Author(
  `type`: Option[String],
  id: String,
  title: String)
  extends Entity

case class Book(
  `type`: Option[String],
  id: String,
  title: String,
  authors: Seq[String])
  extends Entity

case class BookSearchResult(
  numberOfResults: Long,
  suggestions: Seq[String],
  books: Seq[Book])

/**
 * Interface to the business logic of performing searches.
 */
trait SearchService {

  /**
   * Run search using text query.
   */
  def suggestions(searchString: String, offset: Int, count: Int): Future[Seq[Entity]]

  /**
   * Run search using text query.
   */
  def search(searchString: String, offset: Int, count: Int, order: SortOrder): Future[BookSearchResult]

  /**
   * More Like This: find books similar to a given one.
   */
  def findSimilar(id: String, offset: Int, count: Int): Future[BookSearchResult]

}

object SearchService {

  // These ought to move into the Web layer, really.
  val ContributorType = Some("urn:blinkboxbooks:schema:suggestion:contributor")
  val BookType = Some("urn:blinkboxbooks:schema:suggestion:book")

}


