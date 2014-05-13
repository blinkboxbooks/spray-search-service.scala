package com.blinkboxbooks.searchservice

import scala.concurrent.Await
import scala.concurrent.duration._
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.common.util.NamedList
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import com.blinkboxbooks.common.spray.BlinkboxService._
import org.apache.solr.common.params.SolrParams
import org.scalatest.junit.JUnitRunner
import org.apache.solr.client.solrj.response.QueryResponse

@RunWith(classOf[JUnitRunner])
class SolrSearchServiceTests extends FunSuite with BeforeAndAfter {

  var solrServer: SolrServer = _
  var searchService: SolrSearchService = _

  before {
    val response = new QueryResponse()
    //    solrServer = new StubbedSolrServer(response)
    searchService = new SolrSearchService(solrServer)
  }

  ignore("Successful search") {
    fail("TODO")
  }

  ignore("Search with invalid query") {
    fail("TODO")
  }

}

