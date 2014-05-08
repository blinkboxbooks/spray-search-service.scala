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

  // Just some stubbed out data for now.

  override def search(searchString: String, offset: Int, count: Int, order: Option[String], desc: Boolean) = Future {

    val solrQueryString = queryProvider.queryString(searchString)

    val query = new SolrQuery()
    query.setFields(Fields: _*)
    query.setQuery(solrQueryString)
    query.setStart(offset)
    query.setRows(count)

    // TODO: Set the *right* sort order, and stop using the deprecated methods for this.
    for ((sortField, order) <- SortOrders) {
      query.addSortField(sortField, order)
    }

    // Enable highlighting in results, so we know which terms matched.
    query.setHighlight(true)
    for (field <- Fields) {
      query.addHighlightField(field)
    }

    val response = solrServer.query(query)
    toBookSearchResult(response)
  }

  def toBookSearchResult(response: QueryResponse): BookSearchResult = {
    val results = response.getResults
    val books = results.asScala.map(docToBook)

    // TODO: Construct the proper query with suggested spellings.
    val suggestions = response.getSpellCheckResponse.getSuggestions.asScala.map(_.getToken)

    BookSearchResult(results.getNumFound(), suggestions, books.toList)
  }

  def docToBook(doc: SolrDocument): Book =
    new Book(None,
      doc.getFieldValue(ISBN_FIELD).toString,
      doc.getFieldValue(TITLE_FIELD).toString,
      getFields(doc, AUTHOR_FIELD).map(_.toString))

  /** Helper to make SolrJ Java API a bit more helpful, to stop it from returning nulls. */
  // TODO: Make implicit method on SolrDocument!
  def getFields(doc: SolrDocument, fieldName: String): Array[AnyRef] = 
    Option(doc.getFieldValues(fieldName)).getOrElse(Collections.emptyList).toArray

  override def suggestions(query: String, offset: Int, count: Int): Future[List[Entity]] = Future {
    List(
      Book(BookType, "9781443414005", "Bleak House", List("Charles Dickens")),
      Author(ContributorType, "1d1f0d88a461e2e143c44c7736460c663c27ef3b", "Charles Dickens"),
      Book(BookType, "9780141920061", "Hard Times", List("Charles Dickens"))).drop(offset).take(count)
  }

  override def findSimilar(id: String, offset: Int, count: Int): Future[BookSearchResult] = Future {
    BookSearchResult(42, Seq("A suggestion..."), List(
      Book(None, "9781443414005", "Block House", List("Charles Smith")),
      Book(None, "9780141920061", "Happy Times", List("Charles Smith"))).drop(offset).take(count))
  }
}
