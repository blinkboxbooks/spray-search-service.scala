package com.blinkboxbooks.searchservice

import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.common.util.NamedList
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.mockito.Mockito._
import org.mockito.Matchers._

@RunWith(classOf[JUnitRunner])
class SolrSearchServiceTests extends FunSuite with BeforeAndAfter {

  before {
    var solrServer = spy(new StubbedSolrServer())
    var searchService = new SolrSearchService(solrServer)
  }

  test("Successful search") {
    fail("TODO")
  }

  test("Search with invalid query") {
    fail("TODO")
  }

  private class StubbedSolrServer extends SolrServer {
    override def request(request: SolrRequest): NamedList[Object] = new NamedList()
    override def shutdown() {}
  }

}

