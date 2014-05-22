package com.blinkbox.books.search

import scala.concurrent.Future

//
// Domain classes for search results.
//

sealed trait Entity {
  val id: String
  val title: String
}

case class Author(
  id: String,
  title: String)
  extends Entity

case class Book(
  id: String,
  title: String,
  authors: Seq[String])
  extends Entity

sealed trait Suggestion extends Entity

case class AuthorSuggestion(
  id: String,
  title: String)
  extends Suggestion

case class BookSuggestion(
  id: String,
  title: String,
  authors: Seq[String])
  extends Suggestion

case class BookSearchResult(
  numberOfResults: Long,
  suggestions: Seq[String],
  books: Seq[Book])

case class SortOrder(field: String, desc: Boolean)

/**
 * Interface containing the business logic of performing searches.
 */
trait SearchService {

  /**
   * Auto-completion: get suggestions based on characters typed so far.
   */
  def suggestions(searchString: String, offset: Int, count: Int): Future[Seq[Suggestion]]

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

  /** Matcher for ISBN. */
  val Isbn = """^(\d{13})$""".r

}