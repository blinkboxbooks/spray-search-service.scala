package com.blinkboxbooks.searchservice

import com.blinkboxbooks.common.spray.BlinkboxService.SortOrder
import java.util.Collections
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrQuery.ORDER
import org.apache.solr.client.solrj.SolrQuery.SortClause
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SolrSearchService(solrServer: SolrServer) extends SearchService {

  import SolrConstants._

  private val Fields = Array("isbn", "title", "author", "author_guid")
  private val RelevanceOrder = SortOrder("RELEVANCE", true)

  private val queryProvider = new StandardSolrQueryProvider()

  override def search(searchString: String, offset: Int, count: Int, order: SortOrder) = Future {
    val queryStr = queryProvider.queryString(searchString)
    val query = solrQuery(queryStr, offset, count, order)
    val response = solrServer.query(query)
    toBookSearchResult(response)
  }

  override def findSimilar(id: String, offset: Int, count: Int): Future[BookSearchResult] = Future {
    val queryStr = ISBN_FIELD + ":" + id
    val query = solrQuery(queryStr, offset, count, RelevanceOrder).setRequestHandler("/mlt") // TODO: Make req handler configurable
    val response = solrServer.query(query)
    toBookSearchResult(response)
  }

  override def suggestions(searchString: String, offset: Int, count: Int): Future[Seq[Suggestion]] = Future {
    val queryStr = queryProvider.suggestionsQueryString(searchString)
    val query = solrQuery(queryStr, offset, count, RelevanceOrder)
    val response = solrServer.query(query)
    toSuggestions(response)
  }

  // TODO: Should this be a list of orders?
  private def solrQuery(queryStr: String, offset: Int, count: Int, order: SortOrder): SolrQuery = {
    val query = new SolrQuery()
      .setFields(Fields: _*)
      .setQuery(queryStr)
      .setStart(offset)
      .setRows(count)
      .addSort(toSolrSort(order))

    // Enable highlighting of results, so we know which terms matched.
    Fields.foreach(f => query.addHighlightField(f))

    query
  }

  private def toSolrSort(order: SortOrder) =
    SortClause.create(orderToField(order.order), if (order.desc) SolrQuery.ORDER.desc else SolrQuery.ORDER.asc)

  private def orderToField(order: String): String = order match {
    case "RELEVANCE" => SCORE_FIELD
    case "POPULARITY" => VOLUME_FIELD
    case "AUTHOR" => AUTHOR_SORT_FIELD
    case "PRICE" => PRICE_FIELD
    case "PUBLICATION_DATE" => PUBLICATION_DATE_FIELD
    case _ => throw new IllegalArgumentException(s"Unsupported sort order: $order")
  }

  private def toBookSearchResult(response: QueryResponse): BookSearchResult = {
    val books = response.getResults.asScala.map(docToBook(includeType = false))

    // TODO: Construct the proper query with suggested spellings.
    val suggestions = Option(response.getSpellCheckResponse) match {
      case None => Seq()
      case Some(spellCheckResponse) => spellCheckResponse.getSuggestions.asScala.map(_.getToken)
    }

    BookSearchResult(response.getResults.getNumFound(), suggestions, books.toList)
  }

  private def docToBook(includeType: Boolean)(doc: SolrDocument): Book =
    Book(doc.getFieldValue(ISBN_FIELD).toString,
      doc.getFieldValue(TITLE_FIELD).toString,
      getFields(doc, AUTHOR_FIELD).map(_.toString))

  private def docToBookSuggestion(includeType: Boolean)(doc: SolrDocument): BookSuggestion =
    BookSuggestion(doc.getFieldValue(ISBN_FIELD).toString,
      doc.getFieldValue(TITLE_FIELD).toString,
      getFields(doc, AUTHOR_FIELD).map(_.toString))

  /** Return entities for the matched book, as well as the authors of the book. */
  private def docToSuggestions(doc: SolrDocument): Seq[Suggestion] = {
    val bookSuggestion = docToBookSuggestion(includeType = true)(doc)
    val authorNames = getFields(doc, AUTHOR_FIELD).map(_.toString)
    val authorGuids = getFields(doc, AUTHOR_GUID_FIELD).map(_.toString)

    val authors = (authorNames zip authorGuids).map {
      case (authorName, authorGuid) => AuthorSuggestion(authorGuid, authorName)
    }

    Seq(bookSuggestion) ++ authors
  }

  private def toSuggestions(response: QueryResponse): Seq[Suggestion] =
    response.getResults.asScala
      .filter(doc => !hasMissingBookFields(doc))
      .flatMap(docToSuggestions)
      .toList

  private def hasMissingBookFields(doc: SolrDocument) =
    !Option(doc.getFieldValues(AUTHOR_FIELD)).isDefined ||
      !Option(doc.getFieldValue(TITLE_FIELD)).isDefined

  /** Helper to make SolrJ Java API a bit more helpful, to stop it from returning nulls. */
  // TODO: Make implicit method on SolrDocument!
  private def getFields(doc: SolrDocument, fieldName: String): Array[AnyRef] =
    Option(doc.getFieldValues(fieldName)).getOrElse(Collections.emptyList).toArray

}

object SolrConstants {

  // Field names.
  private[searchservice] val SCORE_FIELD = "score"
  private[searchservice] val VOLUME_FIELD = "volume"
  private[searchservice] val PRICE_FIELD = "price"
  private[searchservice] val PUBLICATION_DATE_FIELD = "publication_date"
  private[searchservice] val NAME_FIELD = "name_field"
  private[searchservice] val CONTENT_FIELD = "content_field"
  private[searchservice] val AUTHOR_FIELD = "author"
  private[searchservice] val AUTHOR_EXACT_FIELD = "author_exact_field"
  private[searchservice] val AUTHOR_GUID_FIELD = "author_guid"
  private[searchservice] val AUTHOR_SORT_FIELD = "author_sort"
  private[searchservice] val TITLE_EXACT_FIELD = "title_exact_field"
  private[searchservice] val QUERY_PRICE = "price"
  private[searchservice] val ISBN_FIELD = "isbn"
  private[searchservice] val TITLE_FIELD = "title"

}