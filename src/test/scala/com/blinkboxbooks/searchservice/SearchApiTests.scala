package com.blinkboxbooks.searchservice

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.json4s.jackson.Serialization.write
import org.junit.runner.RunWith
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, FunSuite }
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import spray.http.StatusCodes._
import spray.testkit.ScalatestRouteTest

@RunWith(classOf[JUnitRunner])
class SearchApiTests extends FunSuite with BeforeAndAfter with ScalatestRouteTest with MockitoSugar with SearchApi {

  override val model = mock[SearchModel]
  override val defaultCount = 5
  override val baseUrl = "service/search"

  override implicit def actorRefFactory = system

  before {
    // TODO: Set up mock to return:
    // - Search results for a specific query.
    // - "Invalid" result for a specific query.
    // - Similar books for a known book.
    // - "Unknown" book for similar request. 

  }

  test("simple search for book") {
    // TODO: Add when the model has an API.
    //when(model.search("foo")).thenReturn(Some(expectedSearchResult))

    Get("/search/books?q=some+words") ~> route ~> check {
      contentType === "application/vnd.blinkboxbooks.data.v1+json; charset=UTF-8"
      status === Success

      // TODO: Check count
      // checking if the json response is correct
      // TODO! body.data.asString === write(expectedPromotion)
      // TODO: Check page links
    }
  }

  test("links for first page") {
    fail("TODO!")
  }

  test("links for middle page") {
    fail("TODO!")
  }

  test("links for last page") {
    fail("TODO!")
  }

  test("search for book with all parameters") {
    // TODO: Add when the model has an API.
    //when(model.search("foo")).thenReturn(Some(expectedSearchResult))

    Get("/search/books?q=some+words&count=10&order=POPULARITY&desc=false&offset=20") ~> route ~> check {
      contentType === "application/vnd.blinkboxbooks.data.v1+json; charset=UTF-8"
      status === Success

      // TODO: Check count
      // TODO: Check offset
      // TODO: Check descending setting
      // TODO: Check sort order
      // Check if the json response is correct
      // TODO! body.data.asString === write(expectedPromotion)
    }
  }

  test("returns empty list for search query that matches nothing") {
    //when(model.search(anyString)).thenReturn(List())
    Get("/search/books?q=unmatched") ~> route ~> check {
      status === Success
      fail("TODO: Check empty list is returned")
    }
  }

  test("search with missing query parameter") {
    Get("search/books") ~> route ~> check {
      handled === false
    }
  }

  test("search with missing query parameter and one valid parameter") {
    Get("search/books?limit=10") ~> route ~> check {
      handled === false
    }
  }

  test("returns 500 when we fail to perform search on back-end") {
    fail("TODO: set up mock to throw exception")
    // mockito prevents throwing a checked exception in throws statement, hence this workaround
    //    when(dal.getPromotionById(anyInt)).thenAnswer(new Answer[Nothing] {
    //      override def answer(invocation: InvocationOnMock) = throw new SQLException("Boom!")
    //    })

    Get("/search/books?q=some+query") ~> route ~> check {
      status === InternalServerError
    }
  }

  test("simple query for suggestions") {
    Get("/search/suggestions?q=foo") ~> route ~> check {
      status === Success
      fail("TODO: Check query parameters in request")
      fail("TODO: Check returned JSON")
    }
  }

  test("simple query for suggestions with query parameters") {
    Get("/search/suggestions?q=foo&offset=5&count=15&order=PRICE&desc=false") ~> route ~> check {
      status === Success
      fail("TODO: Check query parameters in request")
      fail("TODO: Check returned JSON")
    }
  }

  test("simple query for similar books") {
    Get("/search/books/12345/similar") ~> route ~> check {
      status === Success
      fail("TODO: Check offset and count in request")
      fail("TODO: Check returned JSON")
    }
  }

  test("query for similar books with query parameters") {
    Get("/search/books/12345/similar?offset=5&count=15") ~> route ~> check {
      status === Success
      fail("TODO: Check offset and count in request")
      fail("TODO: Check returned JSON")
    }
  }

  test("invalid request for similar books, with unwanted slash at end of URL") {
    Get("/search/books/") ~> route ~> check {
      handled === false
    }
  }

  test("invalid request for similar books, with unwanted path elements at end of URL") {
    Get("/search/books/12345/similar/other") ~> route ~> check {
      handled === false
    }
  }

  // TODO: Many other tests...
  // Also: create a separate test case for functional tests, using an embedded in-memory instance of Solr.

}
