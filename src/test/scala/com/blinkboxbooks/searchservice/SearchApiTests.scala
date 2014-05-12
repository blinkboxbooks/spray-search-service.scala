package com.blinkboxbooks.searchservice

import java.io.IOException
import scala.concurrent.Future
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.{ BeforeAndAfter, FunSuite }
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner
import spray.http.StatusCodes._
import spray.testkit.ScalatestRouteTest

import com.blinkboxbooks.common.spray.BlinkboxHelpers._
import SearchApi._
import SearchService._

@RunWith(classOf[JUnitRunner])
class SearchApiTests extends FunSuite with BeforeAndAfter with ScalatestRouteTest with MockitoSugar with SearchApi {

  override val baseUrl = "service/search"
  override def service: SearchService = mockService
  var mockService: SearchService = _

  override implicit def actorRefFactory = system

  val isbn = "1234567890123"

  val searchResults = BookSearchResult(42, Seq("suggested search"), List(
    Book(None, "9781443414005", "Bleak House", List("Charles Dickens")),
    Book(None, "9780141920061", "Hard Times", List("Charles Dickens"))))

  val suggestions = List(
    Book(BookType, "9781443414005", "Bleak House", List("Charles Dickens")),
    Author(ContributorType, "1d1f0d88a461e2e143c44c7736460c663c27ef3b", "Charles Dickens"),
    Book(BookType, "9780141920061", "Hard Times", List("Charles Dickens")))

  val similar = BookSearchResult(101, Seq(), List(
    Book(None, "9781443414005", "Block House", List("Charles Smith")),
    Book(None, "9780141920061", "Happy Times", List("Charles Smith"))))

  before {
    mockService = mock[SearchService]

    // Default mocked behaviour: return results for any query.
    doReturn(Future(searchResults)).when(service).search(anyString, anyInt, anyInt, any[SortOrder])
    doReturn(Future(suggestions)).when(service).suggestions(anyString, anyInt, anyInt)
    doReturn(Future(similar)).when(service).findSimilar(anyString, anyInt, anyInt)
  }

  test("simple search for book") {
    Get("/search/books?q=some+words") ~> route ~> check {
      assert(contentType.value === "application/vnd.blinkboxbooks.data.v1+json")
      assert(status === OK)

      // Check performed query, including default parameters.
      verify(service).search("some words", 0, 50, SortOrder("RELEVANCE", true))

      // Just this once, check the response against the full text of the expected JSON.
      val expectedJson =
        """{
    "type": "urn:blinkboxbooks:schema:search",
    "id": "some words",
    "numberOfResults": 42,
    "suggestions": ["suggested search"],
    "books": [
        {
            "id": "9781443414005",
            "title": "Bleak House",
            "authors": [
                "Charles Dickens"
            ]
        },
        {
            "id": "9780141920061",
            "title": "Hard Times",
            "authors": [
                "Charles Dickens"
            ]
        }
    ],
    "links": [
        {
            "rel": "this",
            "href": "service/search/books?count=50&offset=0"
        }
    ]
    }"""
      // Compare normalised JSON string representations.
      assert(parse(body.data.asString).toString === parse(expectedJson).toString, "Got: \n" + body.data.asString)
    }
  }

  test("search for book with all parameters") {
    val (offset, count) = (5, 10)

    // Set up mock to return search results for expected offset and count only.
    doReturn(Future(searchResults)).when(service)
      .search(anyString, Matchers.eq(offset), Matchers.eq(count), any[SortOrder])

    Get(s"/search/books?q=some+words&count=$count&order=POPULARITY&desc=false&offset=$offset") ~> route ~> check {
      assert(contentType.value === "application/vnd.blinkboxbooks.data.v1+json")
      assert(status === OK)

      // Check request parameters were picked up correctly.
      verify(service).search("some words", offset, count, SortOrder("POPULARITY", false))

      // Check that the JSON response is correct
      val result = parse(body.data.asString).extract[SearchResult]
      assert(result.numberOfResults === searchResults.numberOfResults)

      // Check the expected links, ignoring their order in the returned list.
      val links = result.links.groupBy(_.rel).mapValues(_.head.href)
      assert(links === Map(
        "this" -> s"service/search/books?count=$count&offset=$offset",
        "next" -> s"service/search/books?count=$count&offset=${offset + count}",
        "prev" -> s"service/search/books?count=$count&offset=0"))
    }
  }

  test("returns empty list for search query that matches nothing") {
    doReturn(Future(BookSearchResult(0, Seq(), List())))
      .when(service).search(anyString, anyInt, anyInt, any[SortOrder])

    Get("/search/books?q=unmatched&count=10") ~> route ~> check {
      assert(status === OK)
      val result = parse(body.data.asString).extract[SearchResult]
      assert(result.numberOfResults === 0)

      assert(result.links.size === 1)
      assert(result.links(0) === PageLink("this", s"service/search/books?count=10&offset=0"))
    }
  }

  test("search with missing query parameter") {
    Get("search/books") ~> route ~> check {
      assert(!handled)
    }
  }

  test("search with missing query parameter and one valid parameter") {
    Get("search/books?limit=10") ~> route ~> check {
      assert(!handled)
    }
  }

  test("search with invalid sort order") {
    Get("/search/books?q=some+query&order=UNKNOWN") ~> route ~> check {
      assert(status === BadRequest)
    }
    Get("/search/books?q=some+query&desc=INVALID") ~> route ~> check {
      assert(status === BadRequest)
    }
  }

  test("returns 500 when we fail to perform search on back-end") {
    // Return failure from mock service.
    val ex = new IOException("Test exception")
    doReturn(Future(throw ex)).when(service).search(anyString, anyInt, anyInt, any[SortOrder])

    Get("/search/books?q=some+query") ~> route ~> check {
      assert(status === InternalServerError)
    }
  }

  test("simple query for suggestions") {
    Get("/search/suggestions?q=foo") ~> route ~> check {
      assert(status === OK)
      assert(contentType.value === "application/vnd.blinkboxbooks.data.v1+json")

      val result = parse(body.data.asString).extract[SuggestionsResult]
      assert(result.items === suggestions, "Got: " + body.data.asString)
    }
  }

  test("simple query for suggestions with query parameters") {
    val (offset, count) = (5, 15)

    Get(s"/search/suggestions?q=foo&offset=$offset&count=$count") ~> route ~> check {
      assert(status === OK)

      // Check query parameters in request.
      verify(service).suggestions("foo", offset, count)

      // Check if the json response is correct
      val result = parse(body.data.asString).extract[SuggestionsResult]
      assert(result.items === suggestions)
    }
  }

  test("simple query for similar books") {
    Get(s"/search/books/$isbn/similar") ~> route ~> check {
      assert(status === OK)

      // Check performed query, including default parameters.
      verify(service).findSimilar(isbn, 0, 10)

      // Check returned results.
      val result = parse(body.data.asString).extract[BookSearchResult]
      assert(result === similar)
    }
  }

  test("query for similar books with query parameters") {
    val (offset, count) = (2, 18)
    Get(s"/search/books/$isbn/similar?offset=$offset&count=$count") ~> route ~> check {
      assert(status === OK)

      // Check performed query, including default parameters.
      verify(service).findSimilar(isbn, offset, count)

      // Check returned results.
      val str = body.data.asString
      val result = parse(body.data.asString).extract[BookSearchResult]
      assert(result === similar)
    }
  }

  test("request for similar books with invalid ISBN") {
    // TODO: Refactor to avoid duplication
    Get("/search/books/123456789012/similar") ~> route ~> check {
      assert(!handled)
    }
    Get("/search/books/12345678901234/similar") ~> route ~> check {
      assert(!handled)
    }
    Get("/search/books/xyz/similar") ~> route ~> check {
      assert(!handled)
    }
  }

  test("invalid request for similar books, with unwanted slash at end of URL") {
    Get("/search/books/") ~> route ~> check {
      assert(!handled)
    }
  }

  test("invalid request for similar books, with unwanted path elements at end of URL") {
    Get("/search/books/12345/similar/other") ~> route ~> check {
      assert(!handled)
    }
  }

  // TODO: Many other tests...
  // Also: create a separate test case for functional tests, using an embedded in-memory instance of Solr.

  // *** Check suggestions ***
}
