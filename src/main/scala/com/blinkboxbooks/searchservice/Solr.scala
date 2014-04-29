package com.blinkboxbooks.searchservice

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SolrSearchModel extends SearchModel {

  import SearchModel._

  // Just some stubbed out data for now.

  override def search(query: String, offset: Int, count: Int, order: Option[String], desc: Boolean) = Future {
    BookSearchResult(42, List(
      Book(None, "9781443414005", "Bleak House", List("Charles Dickens")),
      Book(None, "9780141920061", "Hard Times", List("Charles Dickens"))).drop(offset).take(count))
  }

  override def suggestions(query: String, offset: Int, count: Int): Future[List[Entity]] = Future {
    List(
      Book(BookType, "9781443414005", "Bleak House", List("Charles Dickens")),
      Author(ContributorType, "1d1f0d88a461e2e143c44c7736460c663c27ef3b", "Charles Dickens"),
      Book(BookType, "9780141920061", "Hard Times", List("Charles Dickens"))).drop(offset).take(count)
  }

  override def findSimilar(id: String, offset: Int, count: Int): Future[BookSearchResult] = Future {
    BookSearchResult(42, List(
      Book(None, "9781443414005", "Block House", List("Charles Smith")),
      Book(None, "9780141920061", "Happy Times", List("Charles Smith"))).drop(offset).take(count))
  }
}
