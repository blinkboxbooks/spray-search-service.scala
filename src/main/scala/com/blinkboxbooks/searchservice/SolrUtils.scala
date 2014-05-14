package com.blinkboxbooks.searchservice

import java.util.Collection
import java.util.Collections
import org.apache.solr.common.SolrDocument

/**
 * Generally useful helpers for dealing with Solr/SolrJ.
 */
object SolrUtils {

  /**
   * Add some convenient helper methods on SolrDocument to cope with its annoying
   * API that returns Collections that may be null, and untyped objects.
   */
  class SolrDocWrapper(doc: SolrDocument) {

    /**
     *  @return the value of the given field if only one exists, or the first one if several values exist.
     *  @throws IllegalArgumentException if no values are found for the field.
     */
    def getSingleFieldValue(fieldName: String): String =
      Option(doc.getFieldValue(fieldName)) match {
        case Some(obj) if obj.isInstanceOf[Collection[_]] => obj.asInstanceOf[Collection[_]].iterator.next.toString
        case Some(obj) => obj.toString
        case None => throw new IllegalArgumentException(s"No value found for field '$fieldName'")
      }

    def getAllFieldValues(fieldName: String): Array[String] =
      Option(doc.getFieldValues(fieldName)).getOrElse(Collections.emptyList).toArray.map(_.toString)
  }

  implicit def solrDoc2Wrapper(doc: SolrDocument): SolrDocWrapper = new SolrDocWrapper(doc)

}
