package com.blinkboxbooks.common.spray

import com.blinkboxbooks.common.spray.BlinkboxService._

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.junit.runner.RunWith
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, FunSuite }
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlinkboxHelpersTests extends FunSuite with MockitoSugar {

  val baseUrl = "/my/base/url"

  test("links for first page") {
    assert(links(20, offset = 0, count = 5, baseUrl).toSet
      === Set(PageLink("this", s"$baseUrl?count=5&offset=0"), PageLink("next", s"$baseUrl?count=5&offset=5")))
  }

  test("links for middle page") {
    assert(links(20, offset = 5, count = 5, baseUrl).toSet
      === Set(PageLink("this", s"$baseUrl?count=5&offset=5"),
        PageLink("prev", s"$baseUrl?count=5&offset=0"),
        PageLink("next", s"$baseUrl?count=5&offset=10")))
  }

  test("links for last page") {
    assert(links(20, offset = 15, count = 5, baseUrl).toSet
      === Set(PageLink("this", s"$baseUrl?count=5&offset=15"),
        PageLink("prev", s"$baseUrl?count=5&offset=10")))
  }

}
