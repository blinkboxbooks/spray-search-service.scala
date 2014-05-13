package com.blinkboxbooks.searchservice

import com.blinkboxbooks.common.spray.BlinkboxHelpers._
import java.nio.file.Files
import java.io.InputStream
import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.core.CoreContainer
import org.apache.solr.core.SolrConfig
import org.apache.solr.core.SolrResourceLoader
import org.apache.solr.core.ConfigSolr
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrQuery.ORDER
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import scala.collection.JavaConverters._
import spray.testkit.ScalatestRouteTest

import Definitions._
import SearchApi._

@RunWith(classOf[JUnitRunner])
class SearchServiceFunctionalTests extends FunSuite with BeforeAndAfterAll with BeforeAndAfter
  with ScalatestRouteTest with MockitoSugar with SearchApi {

  var rootDir: File = _
  var solrServer: SolrServer = _
  var searchService: SearchService = _

  override val baseUrl = "service/search"
  override val searchTimeout = 5
  override def service: SearchService = searchService
  override implicit def actorRefFactory = system

  /** Set up the embdedded Solr instance once for all tests. */
  override def beforeAll() {
    super.beforeAll()

    // Root directory of Solr config and data files.
    rootDir = Files.createTempDirectory(getClass.getSimpleName).toFile

    // Copy across config files into the directory structure used by the Solr instance.
    copy("/solr/solr.xml", new File(rootDir, "solr.xml"))
    val booksDir = new File(rootDir, "books")
    val booksConfDir = new File(booksDir, "conf")
    booksConfDir.mkdirs()
    copy("/solr/books/conf/solrconfig.xml", new File(booksConfDir, "solrconfig.xml"))
    copy("/solr/books/conf/schema.xml", new File(booksConfDir, "schema.xml"))
    copy("/solr/books/conf/stopwords.txt", new File(booksConfDir, "stopwords.txt"))
    copy("/solr/books/conf/synonyms.txt", new File(booksConfDir, "synonyms.txt"))

    // Launch embedded Solr instance.
    val cores = new CoreContainer(rootDir.getPath)
    cores.load()
    solrServer = new EmbeddedSolrServer(cores, "books")

    addBooks()
  }

  override def afterAll() {
    solrServer.shutdown()
    FileUtils.deleteDirectory(rootDir)
    super.afterAll()
  }

  before {
    searchService = new SolrSearchService(solrServer)
  }

  def addBooks() {
    // A few individual books.
    addBook("1234567890123", "Bob the Builder", Seq("John Smith"), "A ripping yarn about Bob and his cat", 10.0)
    addBook("100000000501", "Walking for Dummies", Seq("Jane Smith"), "Everything you ever wanted to know about walking", 5.0)
    addBook("100000000502", "Walking for Brummies", Seq("John Smythe"), "All the best walks in the Midlands countryside", 6.0)

    // Add a series of very similar books by the same author.
    for (vol <- 1 to 5) {
      // Each book in the series become less popular yet more expensive.
      addBook(s"100000000100${vol}", s"Game of Drones Volume $vol",
        Seq("Martin George"), s"Book $vol in the epic series", price = 10.0 + vol, volume = Some((10 - vol) * 10000))
    }

    // Add some free books.
    addBook("100000000901", "Don Quixote", Seq("Miguel de Cervantes"), "A lone rider and his battle with the windmills", 0.0)
    addBook("100000000902", "Selected Works", Seq("William Shakespare"), "You know what", 0.0)
    addBook("100000000903", "The Count of Monte Cristo", Seq("Alexandre Dumas"), "Blah blah blah", 0.0)

    // Some books with no author.
    addBook("100000000801", "Writer Anonymous", Seq(), "A documentary", 0.0)
    addBook("100000000802", "Ghost Writers", Seq(), "Who wrote this book?", 0.0)

    // Add books with no price data.
    addBook("100000000701", "Superman - my role in his downfall", Seq("Lex Luthor"), "A documentary", None, None)

    solrServer.commit()
  }

  //
  // Search tests.
  //

  test("simple search") {
    Get("/search/books?q=Builder") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults === 1, "Should just match the one book")
      assert(result.books.size === 1)

      val book = result.books.head
      assert(book.title === "Bob the Builder")
      assert(book.authors.toSet == Set("John Smith"))
      assert(result.suggestions === Seq(), "Shouldn't offer spelling suggestions")
    }
  }

  test("search for book not in index") {
    Get("/search/books?q=ATermThatDoesntExistInTheIndex") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults === 0, "Shouldn't match any books")
      assert(result.books.size === 0)
    }
  }

  test("search for author name") {
    Get("/search/books?q=Smythe") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults === 1, "Should just match the one book")
      assert(result.books.size === 1)

      val book = result.books.head
      assert(book.title === "Walking for Brummies")
      assert(book.authors.toSet == Set("John Smythe"))
      assert(result.suggestions === Seq(), "Shouldn't offer spelling suggestions")
    }
  }

  test("search for book and author name") {
    Get("/search/books?q=Bob+Builder+Smith") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults === 2, "Should match on either title or author fields")
      assert(result.books.size === 2)

      val book = result.books.head
      assert(book.title === "Bob the Builder", "The first match should be the one that matches both author and title")

      assert(result.suggestions === Seq(), "Shouldn't offer spelling suggestions")
    }
  }

  test("search for free books") {
    Get("/search/books?q=Free") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults === 3, "Should match all books with a 0 price")
      assert(result.books.size === 3)
    }
  }

  test("search for specific free books") {
    Get("/search/books?q=Free+Smythe") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults === 1, "Should match the specific 0 price")
      assert(result.books.size === 1)
    }
  }

  //
  // Suggestions.
  //

  test("Basic suggestions") {
    Get("/search/suggestions?q=s") ~> route ~> check {
      val results = suggestions
      val numberOfInitialResults = results.items.size
      assert(numberOfInitialResults > 0, "Should get suggestions already from the first character")

      Get("/search/suggestions?q=sm") ~> route ~> check {
        val results = suggestions
        val resultsAfterSecondCharacter = results.items.size
        assert(resultsAfterSecondCharacter < numberOfInitialResults,
          "More characters in query should result in more specific suggestions")

        assert(results.items.exists(item => item.title == "Bob the Builder"))
      }
    }

    Get("/search/suggestions?q=smythe") ~> route ~> check {
      val results = suggestions
      assert(results.items.size === 2, "Should get author and book that match query")

      val entities = results.items.groupBy(_.title)
      assert(entities.contains("John Smythe"))
      assert(entities.contains("Walking for Brummies"))
    }
  }

  test("suggestions when some books are missing fields") {
    fail("TODO")
  }

  //
  // Similar books.
  //

  test("simple query for similar books") {
    Get("/search/books/1234567890123/similar") ~> route ~> check {
      val results = similarBooksResult

      fail("TODO|")
    }
  }

  test("query for similar books with unknown ID") {
    Get("/search/books/1234567890123/similar") ~> route ~> check {
      val results = similarBooksResult

      fail("TODO|")
    }
  }

  //
  // Spelling suggestions.
  //

  test("Spelling suggestion for search with single term") {
    Get("/search/books?q=gamer") ~> route ~> check {
      fail("TODO")
    }
  }

  test("Spelling suggestion for search with multiple terms, one of which is misspelled") {
    Get("/search/books?q=gamer") ~> route ~> check {
      fail("TODO")
    }
  }

  test("Spelling suggestion for search with multiple terms, several of which are misspelled") {
    Get("/search/books?q=gamer") ~> route ~> check {
      fail("TODO")
    }
  }

  private def searchResult = parse(body.data.asString).extract[QuerySearchResult]
  private def similarBooksResult = parse(body.data.asString).extract[SimilarBooksSearchResult]
  private def suggestions = parse(body.data.asString).extract[SuggestionsResult]

  private def copy(input: String, output: File) {
    val inputStream = getClass.getResourceAsStream(input)
    try {
      assert(input != null, s"Couldn't find input file '$input'")
      FileUtils.copyInputStreamToFile(inputStream, output)
    } finally {
      inputStream.close()
    }
  }

  private def addBook(isbn: String, title: String, authors: Seq[String], description: String,
    price: Double, volume: Option[Int] = None) {
    addBook(isbn, title, authors, description, Some(price), volume)
  }

  private def addBook(isbn: String, title: String, authors: Seq[String], description: String,
    price: Option[Double], volume: Option[Int]) {
    val doc = new SolrInputDocument()
    doc.addField(ISBN_FIELD, isbn)
    doc.addField(TITLE_FIELD, title)
    authors.foreach(author => {
      doc.addField(AUTHOR_FIELD, author)
      doc.addField(AUTHOR_SORT_FIELD, author)
      doc.addField(AUTHOR_GUID_FIELD, s"author-guid-$author")
    })
    doc.addField("description", description)
    volume.foreach(v => doc.addField("volume", v))
    price.foreach(p => doc.addField("price", p))
    solrServer.add(doc)
  }

}

