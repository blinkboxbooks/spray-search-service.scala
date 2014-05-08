package com.blinkboxbooks.searchservice

object Definitions {

  // Field names
  val NAME_FIELD = "name_field"
  val CONTENT_FIELD = "content_field"
  val AUTHOR_EXACT = "author_exact_field"
  val TITLE_EXACT = "title_exact_field"
  val QUERY_PRICE = "price"

  /** Regex for matching an ISBN. */
  val Isbn = """^(\d{13})""".r

}
