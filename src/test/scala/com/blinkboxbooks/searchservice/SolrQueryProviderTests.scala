package com.blinkboxbooks.searchservice

import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import StandardSolrQueryProvider._

@RunWith(classOf[JUnitRunner])
class SolrQueryProviderTests extends FunSuite with BeforeAndAfter {

  val provider: StandardSolrQueryProvider = new StandardSolrQueryProvider()

  test("search with empty query") {
    intercept[Exception] { queryString("") }
  }

  test("search with simple single term query") {
    // I think it should be possible to simplify these search queries, e.g. by moving the boosts to the 
    // config of the request handlers.
    assert(queryString("dickens") ===
      """( name_field:(dicken's) OR name_field:(dickens) )^10 OR ( content_field:(dicken's) OR content_field:(dickens) ) OR author_exact_field:("dickens")^25 OR title_exact_field:("dickens")^25""")
  }

  test("search with simple multi-term query") {
    assert(queryString("charles dickens") ===
      """( name_field:(charle's dicken's) OR name_field:(charles dickens) )^10 OR ( content_field:(charle's dicken's) OR content_field:(charles dickens) ) OR ( author_exact_field:("charle's dickens") OR author_exact_field:("charles dickens") )^25 OR ( title_exact_field:("charle's dickens") OR title_exact_field:("charles dickens") )^25""")
  }

  test("search with lower case and trim queries") {
    assert(queryString(" with white space  ") === queryString("with white space"))
    assert(queryString("With MIXED Case") === queryString("with mixed case"))
    assert(queryString("With a mixture Of things ") === queryString("with a mixture of things"))
  }

  test("search for free books") {
    val expectedFreeBookQuery = "price:0"
    assert(queryString("free") === expectedFreeBookQuery)
    assert(queryString(" free  ") === expectedFreeBookQuery)
    assert(queryString("Free") === expectedFreeBookQuery)
    assert(queryString(" Free  ") === expectedFreeBookQuery)
  }

  test("search for specific free books") {
    // This the current behaviour - not convinced this is sensible though: 
    // For example, searching for "free religion" will not find the book "how free can religion be?"
    // as the "free" bit is stripped off.
    assert(queryString("free spirits") === "price:0 AND ( name_field:spirit's OR name_field:spirits )")
    assert(queryString("spirits free") === "price:0 AND ( name_field:spirit's OR name_field:spirits )")
    // TODO: I think the following is a bug in the original code!
    //assert(queryString("free your mind") === "price:0 AND ( name_field:your OR name_field:mind )")
  }

  test("search for ISBN") {
    assert(queryString("1234567890123") === "isbn:1234567890123")
    assert(queryString(" 1234567890123") === "isbn:1234567890123")
    assert(queryString("1234567890123  ") === "isbn:1234567890123")
  }

  test("suggestions for single term") {
    assert(provider.suggestionsQueryString("foo") === "name_field:(foo*)")
  }

  test("suggestions for multiple terms") {
    assert(provider.suggestionsQueryString("foo bar baz") === "name_field:(foo bar baz*)")
  }

  test("suggestions for empty string") {
    intercept[Exception] { provider.suggestionsQueryString("") }
  }

  test("suggestions for search term where apostrophe should be inserted") {
    assert(provider.suggestionsQueryString("enders") === "( name_field:(ender's*) OR name_field:(enders*) )")
    assert(provider.suggestionsQueryString("enders game") === "( name_field:(ender's game*) OR name_field:(enders game*) )")
  }

  test("clean should replace ampersand") {
    assert(clean("&") === "and")
  }

  test("clean invalid characters") {
    assert(clean("!\"£$%^*()_-+=[{]};:'@#~,<.>/?") === "!-;',.")
  }

  test("international characters should go through cleaning unchanged") {
    checkUnchanged("Brönte");
    checkUnchanged("BRÖNTE");
    checkUnchanged("Naïve");
    checkUnchanged("Naive");
    checkUnchanged("The Norwegian alphabet has the three extra letters æ, ø and å.");
    checkUnchanged("The Swedish alphabet has the three extra letters - ä, ö and å.");
    checkUnchanged("John le Carré");
    checkUnchanged("JOHN LE CARRÉ");
    checkUnchanged("The ahs - àáâäæãåā ÀÁÂÄÆÃÅĀ");
    checkUnchanged("The es - èéêëēėę ÈÉÊËĒĖĘ");
    checkUnchanged("The ohs - ôöòóœøōõ ÔÖÒÓŒØŌÕ");
    checkUnchanged("The is - îïíīįì ÎÏÍĪĮÌ");
    checkUnchanged("The us - ûüùúū ÛÜÙÚŪ");
    checkUnchanged("The wys - ÿ Ÿ");
    checkUnchanged("The ens - ñń ÑŃ");
    checkUnchanged("The esses - ßśš ŚŠ");
    checkUnchanged("The els - ł Ł");
    checkUnchanged("The zees - žźż ŽŹŻ");
    checkUnchanged("The cees - çćč ÇĆČ");
  }

  private def queryString(searchString: String) = provider.queryString(searchString)

  private def checkUnchanged(query: String) =
    assert(clean(query) === query.toLowerCase(), "Query '" + query
      + "' should be lowercased, and otherwise unchanged")

}

