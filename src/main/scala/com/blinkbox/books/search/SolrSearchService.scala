package com.blinkbox.books.search

import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrQuery.SortClause
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.response.SpellCheckResponse
import org.apache.solr.common.SolrDocument
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class SolrSearchConfig(
  val freeQueries: Seq[String],
  val nameBoost: Double,
  val contentBoost: Double,
  val exactAuthorBoost: Double,
  val exactTitleBoost: Double) {

  require(freeQueries.size > 0, "Must be configured with at least one search term for free books")
}

class SolrSearchService(config: SolrSearchConfig, solrServer: SolrServer) extends SearchService {

  import SolrConstants._
  import SolrUtils._

  private val Fields = Array("isbn", "title", "author", "author_guid")
  private val RelevanceOrder = SortOrder("RELEVANCE", true)

  private val queryProvider = new StandardSolrQueryProvider(config)

  override def search(searchString: String, offset: Int, count: Int, order: SortOrder) = Future {
    val queryStr = queryProvider.queryString(searchString)

    // Append secondary sort order if required.
    val sortOrder = if (order.field == "RELEVANCE") Seq(order) else Seq(order, RelevanceOrder)

    val query = solrQuery(queryStr, offset, count, sortOrder, spellCheck = true)
    val response = solrServer.query(query)
    toBookSearchResult(response, searchString)
  }

  override def findSimilar(id: String, offset: Int, count: Int): Future[BookSearchResult] = Future {
    val queryStr = ISBN_FIELD + ":" + id
    val query = solrQuery(queryStr, offset, count, Seq(RelevanceOrder), spellCheck = false)
      .setRequestHandler("/mlt") // TODO: Make req handler configurable
    val response = solrServer.query(query)
    toBookSearchResult(response, Seq())
  }

  override def suggestions(searchString: String, offset: Int, count: Int): Future[Seq[Suggestion]] = Future {
    val queryStr = queryProvider.suggestionsQueryString(searchString)
    val sortOrder = Seq(SortOrder("POPULARITY", true), SortOrder("RELEVANCE", true))
    val query = solrQuery(queryStr, offset, count, sortOrder, spellCheck = false)
    val response = solrServer.query(query)
    toSuggestions(response)
  }

  private def solrQuery(queryStr: String, offset: Int, count: Int, orders: Seq[SortOrder], spellCheck: Boolean) = {
    val query = new SolrQuery()
      .setFields(Fields: _*)
      .setQuery(queryStr)
      .setStart(offset)
      .setRows(count)

    // Set sort orders on query.
    orders.foreach(order => query.addSort(toSolrSort(order)))

    // Enable highlighting of results, so we know which terms matched.
    Fields.foreach(f => query.addHighlightField(f))

    query
  }

  private def toSolrSort(order: SortOrder) =
    SortClause.create(orderToField(order.field), if (order.desc) SolrQuery.ORDER.desc else SolrQuery.ORDER.asc)

  private def orderToField(order: String): String = order match {
    case "RELEVANCE" => SCORE_FIELD
    case "POPULARITY" => VOLUME_FIELD
    case "AUTHOR" => AUTHOR_SORT_FIELD
    case "PRICE" => PRICE_FIELD
    case "PUBLICATION_DATE" => PUBLICATION_DATE_FIELD
    case _ => throw new IllegalArgumentException(s"Unsupported sort order: $order")
  }

  private def toBookSearchResult(response: QueryResponse, originalQuery: String): BookSearchResult = {
    val suggestedQueries = Option(response.getSpellCheckResponse) match {
      case None => Seq()
      case Some(spellCheckResponse) =>
        val topReplacements = spellCheckResponse.getSuggestionMap.asScala.toMap
        val updatedQuery = replaceTerms(originalQuery, topReplacements)
        if (updatedQuery != originalQuery) Seq(updatedQuery) else Seq()
    }

    toBookSearchResult(response, suggestedQueries)
  }

  private def toBookSearchResult(response: QueryResponse, suggestedQueries: Seq[String]) = {
    val (count, books) = Option(response.getResults) match {
      case Some(results) => (results.getNumFound, results.asScala.map(docToBook(includeType = false)).toSeq)
      case None => (0L, Seq())
    }
    BookSearchResult(count, suggestedQueries, books)
  }

  /** Collate suggested alternative spellings for terms into a single new search query. */
  private def replaceTerms(originalQuery: String, replacements: Map[String, SpellCheckResponse.Suggestion]): String = {
    val terms = originalQuery.split("\\s")
    val updatedTerms = terms.map(originalTerm => replacements.get(originalTerm) match {
      case Some(suggestion) => suggestion.getAlternatives().get(0)
      case _ => originalTerm
    })
    updatedTerms.mkString(" ")
  }

  private def docToBook(includeType: Boolean)(doc: SolrDocument): Book =
    Book(doc.getSingleFieldValue(ISBN_FIELD),
      doc.getSingleFieldValue(TITLE_FIELD),
      doc.getAllFieldValues(AUTHOR_FIELD))

  private def docToBookSuggestion(includeType: Boolean)(doc: SolrDocument): BookSuggestion =
    BookSuggestion(doc.getSingleFieldValue(ISBN_FIELD),
      doc.getSingleFieldValue(TITLE_FIELD),
      doc.getAllFieldValues(AUTHOR_FIELD))

  /** Return entities for the matched book, as well as the authors of the book. */
  private def docToSuggestions(doc: SolrDocument): Seq[Suggestion] = {
    val bookSuggestion = docToBookSuggestion(includeType = true)(doc)
    val authorNames = doc.getAllFieldValues(AUTHOR_FIELD)
    val authorGuids = doc.getAllFieldValues(AUTHOR_GUID_FIELD)

    val authors = (authorNames zip authorGuids).map {
      case (authorName, authorGuid) => AuthorSuggestion(authorGuid, authorName)
    }

    Seq(bookSuggestion) ++ authors
  }

  private def toSuggestions(response: QueryResponse): Seq[Suggestion] =
    response.getResults.asScala
      .filter(doc => !hasMissingBookFields(doc))
      .flatMap(docToSuggestions)
      .toSeq

  private def hasMissingBookFields(doc: SolrDocument) =
    !Option(doc.getFieldValues(AUTHOR_FIELD)).isDefined ||
      !Option(doc.getFieldValue(TITLE_FIELD)).isDefined

}

object SolrConstants {

  // Field names.
  private[search] val ISBN_FIELD = "isbn"
  private[search] val TITLE_FIELD = "title"
  private[search] val SCORE_FIELD = "score"
  private[search] val NAME_FIELD = "name_field"
  private[search] val CONTENT_FIELD = "content_field"
  private[search] val PUBLICATION_DATE_FIELD = "publication_date"
  private[search] val VOLUME_FIELD = "volume"
  private[search] val PRICE_FIELD = "price"
  private[search] val AUTHOR_FIELD = "author"
  private[search] val AUTHOR_EXACT_FIELD = "author_exact_field"
  private[search] val AUTHOR_GUID_FIELD = "author_guid"
  private[search] val AUTHOR_SORT_FIELD = "author_sort"
  private[search] val TITLE_EXACT_FIELD = "title_exact_field"

}