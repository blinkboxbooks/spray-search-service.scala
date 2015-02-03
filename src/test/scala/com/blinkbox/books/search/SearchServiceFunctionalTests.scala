package com.blinkbox.books.search

import java.net.URLEncoder
import java.nio.file.Files
import java.io.InputStream
import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.core.CoreContainer
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
import scala.concurrent.duration._
import spray.http.StatusCodes._
import spray.testkit.ScalatestRouteTest

import SearchApi._
import SolrConstants._

@RunWith(classOf[JUnitRunner])
class SearchServiceFunctionalTests extends FunSuite with BeforeAndAfterAll with BeforeAndAfter
  with ScalatestRouteTest with MockitoSugar with SearchApi {

  var rootDir: File = _
  var solrServer: SolrServer = _
  var searchService: SearchService = _

  override def service = searchService
  override val apiConfig = ApiConfig("localhost", 8080, "service/search", 5.seconds, "*", 100.seconds, 200.seconds)

  override implicit def actorRefFactory = system

  val SeriesCount = 5

  val searchConfig = new QueryConfig(Seq("free books", "free", "test free books"), 1.0, 1.5, 2.0, 3.0)

  /** Set up the embedded Solr instance once for all tests. */
  override def beforeAll(): Unit = {
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

    solrServer.commit()
  }

  override def afterAll(): Unit = {
    solrServer.shutdown()
    FileUtils.deleteDirectory(rootDir)
    super.afterAll()
  }

  before {
    searchService = new SolrSearchService(searchConfig, solrServer)
  }

  def addBooks(): Unit = {
    // A few individual books.
    addBook("1234567890123", "Bob the Builder", Seq("John Smith"), "A ripping yarn about Bob and his cat", 10.0)
    addBook("100000000501", "Walking for Dummies", Seq("Jane Smith"), "Everything you ever wanted to know about walking", 5.0)
    addBook("100000000502", "Walking for Brummies", Seq("John Smythe"), "All the best walks in the Midlands countryside", 6.0)

    // Add a series of very similar books.
    for (vol <- 1 to SeriesCount) {
      // Each book in the series become less popular yet more expensive.
      addBook(s"100000000100${vol}", s"Game of Drones Volume $vol",
        Seq("Martin George"), s"Book $vol in the epic series", price = 10.0 + vol, volume = Some((10 - vol) * 10000))
    }

    // Add some more books by the author that wrote the series above.
    addBook(s"1000000002001", s"My Epic Life",
      Seq("Martin George"), s"The story of the genius author", price = 25.0)
    addBook(s"1000000002001", s"101 Porridge Success Recipes",
      Seq("Martin George"), s"Every variety of porridge you can imagine and then some", price = 7.50)

    // Add some free books.
    addBook("100000000901", "Don Quixote", Seq("Miguel de Cervantes"), "A lone rider and his battle with the windmills", 0.0)
    addBook("100000000902", "Selected Works", Seq("William Shakespare"), "You know what", 0.0)
    addBook("100000000903", "The Count of Monte Cristo", Seq("Alexandre Dumas"), "Blah blah blah", 0.0)

    // Some books with no author.
    addBook("100000000801", "Writer Anonymous", Seq(), "A documentary", 3.49)
    addBook("100000000802", "Ghost Writers", Seq(), "Who wrote this book?", 4.49)
    addBook("100000000803", "Selected Secrets", Seq(), "You know what", 5.00)

    // Add books with no price data.
    addBook("100000000701", Some("Superman - my role in his downfall"), Seq("Lex Luthor"), "A documentary", None, None)
  }

  //
  // Search tests.
  //

  test("simple search") {
    Get("/search/books?q=Builder") ~> route ~> check {
      assert(status == OK)
      val result = searchResult
      assert(result.numberOfResults == 1 &&
        result.books.size == 1, "Should just match the one book")

      val book = result.books.head
      assert(book.title == "Bob the Builder" &&
        book.authors.toSet == Set("John Smith") &&
        result.suggestions == Seq(), "Shouldn't offer spelling suggestions")
    }
  }

  test("searches with different capitalisation and whitespace") {
    checkSameSearchResults(1, "Bob Builder", "bob  builder ", "  bob BuIlDer ")
  }

  test("search by descending vs. ascending order") {
    Get("/search/books?q=Builder&desc=false") ~> route ~> check {
      val ascendingResult = searchResult
      Get("/search/books?q=Builder&desc=false") ~> route ~> check {
        val descendingResult = searchResult
        assert(ascendingResult.numberOfResults == descendingResult.numberOfResults, "Should get same number of results")
        assert(ascendingResult.books == descendingResult.books.reverse, "Results should be identical but in reverse order")
      }
    }
  }

  test("search for book not in index") {
    Get("/search/books?q=ATermThatDoesntExistInTheIndex") ~> route ~> check {
      val result = searchResult
      assert(status == OK &&
        result.numberOfResults == 0 &&
        result.books.size == 0, "Shouldn't match any books")
    }
  }

  test("search for author name") {
    Get("/search/books?q=Smythe") ~> route ~> check {
      val result = searchResult
      assert(result.numberOfResults == 1 &&
        result.books.size == 1, "Should just match the one book")

      val book = result.books.head
      assert(book.title == "Walking for Brummies" &&
        book.authors.toSet == Set("John Smythe") &&
        result.suggestions == Seq())
    }
  }

  test("search for book and author name") {
    Get("/search/books?q=Bob+Builder+Smith") ~> route ~> check {
      val result = searchResult
      assert(result.books.size == 2 &&
        result.numberOfResults == 2, "Should match on either title or author fields")

      val book = result.books.head
      assert(book.title == "Bob the Builder", "The first match should be the one that matches both author and title")

      assert(result.suggestions == Seq(), "Shouldn't offer spelling suggestions")
    }
  }

  test("search for free books") {
    for (
      configuredQuery <- searchConfig.freeQueries;
      query <- Seq(configuredQuery, configuredQuery.toLowerCase, configuredQuery.toUpperCase);
      encodedQuery = URLEncoder.encode(query, "UTF-8")
    ) {
      Get(s"/search/books?q=$encodedQuery") ~> route ~> check {
        val result = searchResult
        assert(result.books.size == 3 &&
          result.numberOfResults == 3, s"Should match all books with a 0 price when searching for '$query'")
      }
    }
  }

  test("search for specific free books") {
    Get("/search/books?q=Free+Selected") ~> route ~> check {
      val result = searchResult
      assert(result.books.size == 1 &&
        result.numberOfResults == 1, "Should match the specific 0 price")
    }
  }

  test("search using non-ASCII characters") {
    // Check that accented characters are treated as un-accented, in book, author and title fields.
    addBook("990000000101", "Smiley's People", Seq("John le Carré"), "The classic spy novel", 6.0)
    addBook("990000000102", "John le Carré - the Biography", Seq("Anonymous"), "The story of the spy novelist", 6.0)
    addBook("990000000103", "Another Spy Novel", Seq("S. Omeone"), "'Really awesome' - John le Carré", 6.0)
    solrServer.commit()

    // Should do synonym substitution, hence match both spellings.
    checkSameSearchResults(3, "Carré", "Carre")
  }

  test("synonym substitution") {
    addBook("990000000101", "The Color Purple", Seq("Alice Walker"), "Gritty realism", 6.0)
    addBook("990000000102", "The Colour of Memory", Seq("Geoff Dyer"), "80s Nostalgia", 6.0)
    solrServer.commit()

    // Should do synonym substitution, hence match both spellings.
    checkSameSearchResults(2, "color", "colour")
  }

  test("the Ender's Game fix") {
    // See CP-771.
    addBook("990000000101", "Ender's Game", Seq("Orson Scott Card"), "", 6.0)
    solrServer.commit()

    // Should cater for people who don't know how to use apostrophes.
    checkSameSearchResults(1, "enders", "ender's")
  }

  private def checkSameSearchResults[T](expectedNumber: Int, requests: String*): Unit = {
    val requestUrls = requests.map(request => "/search/books?q=" + URLEncoder.encode(request, "UTF-8"))
    val results = requestUrls.map(requestUrl => Get(requestUrl) ~> route ~> check { searchResult.books })
    val distinctResults = results.toSet

    assert(results(0).size == expectedNumber, s"Should get $expectedNumber results for search, got ${results.size}")
    assert(distinctResults.size == 1, s"Results for searches '$requests' 'should all be the same, got: $distinctResults")
  }

  //
  // Suggestions.
  //

  // TODO!
  ignore("Basic suggestions") {
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
      assert(results.items.size == 2, "Should get author and book that match query")

      val entities = results.items.groupBy(_.title)
      assert(entities.contains("John Smythe"))
      assert(entities.contains("Walking for Brummies"))
    }
  }

  test("suggestions when query has no matches") {
    Get("/search/suggestions?q=qwerty") ~> route ~> check {
      assert(status == OK)
      val results = suggestions
      assert(results.items == List(), "Should get empty list of suggestions")
    }
  }

  test("suggestions when some books are missing fields") {
    // Add a book with no title.
    val id = "1122334455667"
    addBook(id, None, Seq("John Smith"), "This book has no title", Some(10.0), Some(1000))

    // Check that suggestions ignore this book.
    Get("/search/suggestions?q=john+smith") ~> route ~> check {
      assert(status == OK)
      // TODO!
      //val results = suggestions
      //assert(!suggestions.items.exists(item => item.id == id))
    }
  }

  //
  // Similar books.
  //

  test("simple query for similar books") {
    // Get one book in a series and check that the top results
    // are the other books in the series.
    val inputIsbn = "1000000001001"
    Get(s"/search/books/$inputIsbn/similar") ~> route ~> check {
      assert(status == OK)
      val results = similarBooksResult
      results.books.take(SeriesCount - 1).foreach(book => {
        assert(book.authors == Seq("Martin George"), s"Book should be in same series, got: $book")
        assert(book.id != inputIsbn)
      })
    }
  }

  test("query for similar books with unknown ID") {
    // Make a request that results in no books coming back.
    Get("/search/books/9999999999999/similar") ~> route ~> check {
      assert(status == OK)
      val results = similarBooksResult
      assert(results.numberOfResults == 0)

      // The code for this need fixing as getResults in the Solr response returns null when nothing was found (ugh!).
    }
  }

  //
  // Spelling suggestions.
  //

  test("Spelling suggestion for search with single term") {
    Get("/search/books?q=gamex") ~> route ~> check {
      assert(searchResult.suggestions == List("game"))
    }
  }

  test("Spelling suggestion for search with multiple terms, one of which is misspelled") {
    Get("/search/books?q=game+of+dronxs") ~> route ~> check {
      assert(searchResult.suggestions == List("game of drones"))
    }
  }

  test("Spelling suggestion for search with multiple terms, several of which are misspelled") {
    Get("/search/books?q=grame+of+dronxs") ~> route ~> check {
      assert(searchResult.suggestions == List("game of drones"))
    }
  }

  test("Terms that are similar enough to known words to cause a spelling suggestion") {
    for (searchString <- Seq("grame", "gamer", "gamert", "gbme", "gamte")) {
      Get(s"/search/books?q=$searchString") ~> route ~> check {
        assert(searchResult.suggestions == List("game"), s"Search for '$searchString' should product spelling suggestion")
      }
    }
  }

  test("Terms that are too different from any known words") {
    for (searchString <- Seq("tiddlywinks", "frone", "gramers")) {
      Get(s"/search/books?q=$searchString") ~> route ~> check {
        val suggestions = searchResult.suggestions
        assert(suggestions == List(), s"Search for '$searchString' shouldn't result in suggestions, got '$suggestions'")
      }
    }
  }

  private def searchResult = parse(body.data.asString).extract[QuerySearchResult]
  private def similarBooksResult = parse(body.data.asString).extract[SimilarBooksSearchResult]
  private def suggestions = parse(body.data.asString).extract[SuggestionsResult]

  private def copy(input: String, output: File): Unit = {
    val inputStream = getClass.getResourceAsStream(input)
    try {
      assert(input != null, s"Couldn't find input file '$input'")
      FileUtils.copyInputStreamToFile(inputStream, output)
    } finally {
      inputStream.close()
    }
  }

  private def addBook(isbn: String, title: String, authors: Seq[String], description: String,
                      price: Double, volume: Option[Int] = None): Unit = {
    addBook(isbn, Some(title), authors, description, Some(price), volume)
  }

  private def addBook(isbn: String, title: Option[String], authors: Seq[String], description: String,
                      price: Option[Double], volume: Option[Int]): Unit = {
    val doc = new SolrInputDocument()
    doc.addField(ISBN_FIELD, isbn)
    title.foreach(t => doc.addField(TITLE_FIELD, t))
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
