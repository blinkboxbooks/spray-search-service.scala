package com.blinkboxbooks.searchservice

import org.apache.commons.lang.StringUtils
import com.google.common.base.CharMatcher
import scala.util.{ Try, Success, Failure }

import QueryBuilder.Operator
import Definitions._

/** Common interface for things that takes user searches and turns them to Solr queries. */
trait SolrQueryProvider {

  /**
   * Takes a search string as entered by a user, and returns a valid Solr query,
   * with the appropriate grouping, bracketing, boosts etc.
   */
  def queryString(searchString: String): String

  /**
   * Takes a search string as typed by a user, and returns a valid Solr query
   * that will return auto-complete suggestions for the characters the user has typed so far.
   */
  def suggestionsQueryString(searchString: String): String

}

/**
 *  Wraps up the query processing as done in the previous version of the search service.
 */
class StandardSolrQueryProvider extends SolrQueryProvider {

  import StandardSolrQueryProvider._

  override def queryString(searchString: String): String = {

    val query = clean(searchString) match {
      case q if StringUtils.isBlank(q) => throw new Exception(s"Invalid or empty search term: $q")
      case Isbn(isbn) => QueryBuilder.build("isbn", isbn)
      case q if q.startsWith("free ") => buildFreeQuery(q.substring(5))
      case q if q.endsWith(" free") => buildFreeQuery(q.substring(0, q.length() - 5))
      case q => buildQuery(q)
    }

    query.toString
  }

  override def suggestionsQueryString(searchString: String): String = clean(searchString) match {
    case cleaned if StringUtils.isBlank(cleaned) => throw new Exception(s"Invalid or empty search string: $searchString")
    case cleaned => new QueryBuilder(Operator.OR, true)
      .append(NAME_FIELD, cleaned, true)
      .toString
  }

}

object StandardSolrQueryProvider {

  val freeBooksQueries = Seq("free")
  val nameFieldBoost = 10
  val contentFieldBoost = 1
  val authorExactBoost = 25
  val titleExactBoost = 25

  val validCharacterMatcher = CharMatcher.JAVA_LETTER_OR_DIGIT
    .or(CharMatcher.anyOf("-,.';!"))
    .or(CharMatcher.WHITESPACE)

  /**
   * Normalise query, by stripping off excess whitespace and removing invalid characters.
   */
  // #CP-213: Strip invalid characters
  // #CP-860: Don't strip out accented and other international characters.
  // #CP-188: Replace ampersands
  def clean(query: String): String =
    validCharacterMatcher.retainFrom(query.trim().toLowerCase().replaceAll("&", "and"))

  /** Build normal meta-data search. */
  def buildQuery(queryString: String): QueryBuilder =
    new QueryBuilder(Operator.OR, true)
      .append(NAME_FIELD, queryString, nameFieldBoost)
      .append(CONTENT_FIELD, queryString, contentFieldBoost)
      .append(AUTHOR_EXACT_FIELD, queryString, authorExactBoost)
      .append(TITLE_EXACT_FIELD, queryString, titleExactBoost)

  /** Builds a free books query with a subject extracted from the search term. */
  def buildFreeQuery(subject: String): QueryBuilder =
    new QueryBuilder(Operator.AND, false)
      .append(QUERY_PRICE, "0")
      .append(NAME_FIELD, subject)

}

