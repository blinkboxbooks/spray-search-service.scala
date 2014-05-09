package com.blinkboxbooks.searchservice

object Definitions {

  // Field names.
  val SCORE_FIELD = "score"
  val VOLUME_FIELD = "volume"
  val PRICE_FIELD = "price"
  val PUBLICATION_DATE_FIELD = "publication_date"
  val NAME_FIELD = "name_field"
  val CONTENT_FIELD = "content_field"
  val AUTHOR_EXACT_FIELD = "author_exact_field"
  val TITLE_EXACT_FIELD = "title_exact_field"
  val QUERY_PRICE = "price"
  val ISBN_FIELD = "isbn"
  val TITLE_FIELD = "title"
  val AUTHOR_FIELD = "author"
  val AUTHOR_GUID_FIELD = "author_guid"

  /** Regex for matching an ISBN. */
  val Isbn = """^(\d{13})""".r
 
}
