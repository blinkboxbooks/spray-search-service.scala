package com.blinkboxbooks.searchservice

import java.nio.file.Files
import java.io.File
import org.junit.runner.RunWith
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.core.CoreContainer
import org.apache.solr.core.SolrResourceLoader
import org.apache.solr.core.ConfigSolr
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrQuery.ORDER
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import org.apache.solr.core.SolrConfig
import java.io.InputStream

@RunWith(classOf[JUnitRunner])
class SearchServiceFunctionalTests extends FunSuite with BeforeAndAfter with BeforeAndAfterAll with MockitoSugar {

  // TODO!!! Move these into the implementation.
  val Fields = Array("isbn", "title", "author")
  val SortOrders = Seq(("score", ORDER.desc), ("price", ORDER.asc), ("title", ORDER.asc))

  var rootDir: File = _
  var solrServer: SolrServer = _

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
    val booksContainer = new CoreContainer(rootDir.getPath)
    booksContainer.load()
    solrServer = new EmbeddedSolrServer(booksContainer, "books")
  }

  override def afterAll() {
    solrServer.shutdown()
    FileUtils.deleteDirectory(rootDir)
    super.afterAll()
  }

  test("smoke test") {
    addBook("1234567890123", "Bob the Builder", Seq("John Smith"), "A ripping yarn about Bob and his cat", 10.0)
    solrServer.commit()

    // TODO: Remove this when we have tests that hit the index through the service.
    // So far this just checks that the index has been set up correctly.
    val query = new SolrQuery()
    query.setFields(Fields: _*)
    query.setQuery("borb".trim.toLowerCase)
    query.setStart(5)
    query.setRows(10)
    for ((sortField, order) <- SortOrders) {
      // TODO: Have a look at use addSort() instead.
      query.addSortField(sortField, order)
    }
    query.setHighlight(true)
    for (field <- Fields) {
      query.addHighlightField(field)
    }

    val response = solrServer.query(query)
    val results = response.getResults
    println(s"Found ${results.getNumFound()} results")
    results.asScala.foreach { result =>
      println(s"Result: $result")
      val isbn = result.getFieldValue("isbn")
      val highlights = response.getHighlighting().get(isbn)
      println(s"\tMatched fields: ${highlights.keySet}")
      println(s"\tHighlights: $highlights")
    }

    val spellcheckResponse = response.getSpellCheckResponse
    if (!spellcheckResponse.isCorrectlySpelled) {
      for (suggestion <- spellcheckResponse.getSuggestions.asScala) {
        println(s"\tSuggestions: spell ${suggestion.getToken} as ${suggestion.getSuggestions().asScala.mkString(" or ")}")
      }
    }
  }

  test("search for book not in index") {
    fail("TODO")
  }

  test("search for author name") {
    fail("TODO")
  }

  test("search for book name") {
    fail("TODO")
  }

  test("search for book and author name") {
    fail("TODO")
  }

  test("search for free books") {
    fail("TODO")
  }

  test("search for specific free books") {
    fail("TODO")
  }

  private def copy(input: String, output: File) = {
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
    val doc = new SolrInputDocument()
    doc.addField("isbn", isbn)
    doc.addField("title", title)
    authors.foreach(author => doc.addField("author", author))
    doc.addField("description", description)
    volume.foreach(vol => doc.addField("volume", vol))
    doc.addField("price", price)
    solrServer.add(doc)
  }

}
