package com.blinkboxbooks.searchservice

import com.blinkboxbooks.common.spray.BlinkboxHelpers.SortOrder
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrQuery.ORDER
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import Definitions._
import java.util.Collections
import org.apache.solr.client.solrj.SolrQuery.SortClause

class SolrSearchService(solrServer: SolrServer) extends SearchService {

  import SearchService._

  val Fields = Array("isbn", "title", "author")
  val SortOrders = Seq(("score", ORDER.desc), ("price", ORDER.asc), ("title", ORDER.asc))
  val RelevanceOrder = SortOrder("RELEVANCE", true)

  val queryProvider = new StandardSolrQueryProvider()

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

  override def suggestions(searchString: String, offset: Int, count: Int): Future[Seq[Entity]] = Future {
    val queryStr = queryProvider.suggestionsQueryString(searchString)
    val query = solrQuery(queryStr, offset, count, RelevanceOrder)
    val response = solrServer.query(query)
    toSuggestions(response)
  }

  // TODO: Should this be a list of orders??
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
    case "AUTHOR" => AUTHOR_FIELD
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
    new Book(if (includeType) BookType else None,
      doc.getFieldValue(ISBN_FIELD).toString,
      doc.getFieldValue(TITLE_FIELD).toString,
      getFields(doc, AUTHOR_FIELD).map(_.toString))

  /** Return entities for the matched book, as well as the authors of the book. */
  private def docToEntities(doc: SolrDocument): Seq[Entity] = {
    val book = docToBook(includeType = true)(doc)
    val authorNames = getFields(doc, AUTHOR_FIELD).map(_.toString)
    val authorGuids = getFields(doc, AUTHOR_GUID_FIELD).map(_.toString)

    val authors = (authorNames zip authorGuids).map {
      case (authorName, authorGuid) => new Author(ContributorType, authorName, authorGuid)
    }

    Seq(book) ++ authors
  }

  private def toSuggestions(response: QueryResponse): Seq[Entity] =
    response.getResults.asScala
      .filter(doc => !hasMissingBookFields(doc))
      .flatMap(docToEntities)
      .toList

  private def hasMissingBookFields(doc: SolrDocument) =
    !Option(doc.getFieldValues(AUTHOR_FIELD)).isDefined ||
      !Option(doc.getFieldValue(TITLE_FIELD)).isDefined

  /** Helper to make SolrJ Java API a bit more helpful, to stop it from returning nulls. */
  // TODO: Make implicit method on SolrDocument!
  private def getFields(doc: SolrDocument, fieldName: String): Array[AnyRef] =
    Option(doc.getFieldValues(fieldName)).getOrElse(Collections.emptyList).toArray

}
