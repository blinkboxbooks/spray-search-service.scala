package com.blinkboxbooks.searchservice

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SolrSearchModel extends SearchModel {

  // TODO: stubbed out data, replace with the real stuff...

  override def search(query: String, offset: Int, count: Int, order: Option[String], desc: Boolean): Future[List[Book]] = Future {
    List(
      new Book("9781443414005", "Bleak House", List("Charles Dickens")),
      new Book("9780141920061", "Hard Times", List("Charles Dickens")))
  }

  override def suggestions(query: String, offset: Int, count: Int): Future[List[Entity]] = Future {
    List(
      new Book("9781443414005", "Bleak House", List("Charles Dickens")),
      new Author("1d1f0d88a461e2e143c44c7736460c663c27ef3b", "Charles Dickens"),
      new Book("9780141920061", "Hard Times", List("Charles Dickens")))
  }

  override def findSimilar(id: String, offset: Int, count: Int): Future[List[Book]] = Future {
    List(
      Book(None, "9781443414005", "Block House", List("Charles Smith")),
      Book(None, "9780141920061", "Happy Times", List("Charles Smith")))
  }

}
