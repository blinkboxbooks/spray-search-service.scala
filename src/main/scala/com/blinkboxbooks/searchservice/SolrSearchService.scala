package com.blinkboxbooks.searchservice

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

class SolrSearchService(solrServer: SolrServer) extends SearchService {

  import SearchService._

  val Fields = Array("isbn", "title", "author")
  val SortOrders = Seq(("score", ORDER.desc), ("price", ORDER.asc), ("title", ORDER.asc))

  val queryProvider = new StandardSolrQueryProvider()

  override def search(searchString: String, offset: Int, count: Int, order: Option[String], desc: Boolean) = Future {
    val query = solrQuery(queryProvider.queryString(searchString), offset, count)
    val response = solrServer.query(query)
    toBookSearchResult(response)
  }

  override def suggestions(query: String, offset: Int, count: Int): Future[List[Entity]] = Future {
    List(
      Book(BookType, "9781443414005", "Bleak House", List("Charles Dickens")),
      Author(ContributorType, "1d1f0d88a461e2e143c44c7736460c663c27ef3b", "Charles Dickens"),
      Book(BookType, "9780141920061", "Hard Times", List("Charles Dickens"))).drop(offset).take(count)
  }

  override def findSimilar(id: String, offset: Int, count: Int): Future[BookSearchResult] = Future {
    val query = solrQuery(ISBN_FIELD + ":" + id, offset, count).setRequestHandler("/mlt")
    val response = solrServer.query(query)
    toBookSearchResult(response)
  }
  
  private def solrQuery(queryStr: String, offset: Int, count: Int): SolrQuery = {
    val query = new SolrQuery()
      .setFields(Fields: _*)
      .setQuery(queryStr)
      .setStart(offset)
      .setRows(count)

    // Enable highlighting in results, so we know which terms matched.
    query.setHighlight(true)
    Fields.foreach(f => query.addHighlightField(f))

    // TODO: Set the *right* sort order, and stop using the deprecated methods for this.
    for ((sortField, order) <- SortOrders) {
      query.addSortField(sortField, order)
    }

    query
  }

  private def toBookSearchResult(response: QueryResponse): BookSearchResult = {
    val books = response.getResults.asScala.map(docToBook)

    // TODO: Construct the proper query with suggested spellings.
    val suggestions = Option(response.getSpellCheckResponse) match {
      case None => Seq()
      case Some(spellCheckResponse) => spellCheckResponse.getSuggestions.asScala.map(_.getToken)
    }

    BookSearchResult(response.getResults.getNumFound(), suggestions, books.toList)
  }

  private def docToBook(doc: SolrDocument): Book =
    new Book(None,
      doc.getFieldValue(ISBN_FIELD).toString,
      doc.getFieldValue(TITLE_FIELD).toString,
      getFields(doc, AUTHOR_FIELD).map(_.toString))

  /** Helper to make SolrJ Java API a bit more helpful, to stop it from returning nulls. */
  // TODO: Make implicit method on SolrDocument!
  private def getFields(doc: SolrDocument, fieldName: String): Array[AnyRef] =
    Option(doc.getFieldValues(fieldName)).getOrElse(Collections.emptyList).toArray

}
