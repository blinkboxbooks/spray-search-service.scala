package com.blinkbox.books.search

import com.blinkbox.books.config.RichConfig
import com.typesafe.config.Config
import scala.concurrent.duration.FiniteDuration

/**
 * Top level configuration object for search service.
 */
case class AppConfig(api: ApiConfig, solr: SolrConfig, query: QueryConfig)

object AppConfig {
  def apply(config: Config): AppConfig = {
    AppConfig(
      ApiConfig(config.getConfig("api")),
      SolrConfig(config.getConfig("solr")),
      QueryConfig(config.getConfig("query")))
  }
}

/**
 * Configuration for the service HTTP API.
 */
case class ApiConfig(
  hostname: String,
  port: Int,
  path: String,
  searchTimeout: FiniteDuration,
  corsOrigin: String,
  searchMaxAge: FiniteDuration,
  autoCompleteMaxAge: FiniteDuration)

object ApiConfig {
  def apply(config: Config): ApiConfig = ApiConfig(
    config.getString("hostname"),
    config.getInt("port"),
    config.getString("path"),
    config.getFiniteDuration("searchTimeout"),
    config.getString("httpCorsOrigin"),
    config.getFiniteDuration("searchMaxAge"),
    config.getFiniteDuration("autocompleteMaxAge"))
}

/**
 * Configuration for the Solr instance that this service delegates requests to.
 */
case class SolrConfig(
  hostname: String,
  port: Int,
  rootPath: String,
  index: String) {
  val url = s"http://$hostname:$port$rootPath/$index"
}

object SolrConfig {
  def apply(config: Config): SolrConfig = SolrConfig(
    config.getString("hostname"),
    config.getInt("port"),
    config.getString("rootPath"),
    config.getString("index"))
}

/**
 * Detailed configuration for search query parameters.
 */
case class QueryConfig(
  freeQueries: Seq[String],
  nameBoost: Double,
  contentBoost: Double,
  exactAuthorBoost: Double,
  exactTitleBoost: Double) {
  require(freeQueries.size > 0, "Must be configured with at least one search term for free books")
}

object QueryConfig {
  def apply(config: Config): QueryConfig =
    QueryConfig(
      config.getString("freeQueries").split(",").map(_.toLowerCase.trim),
      config.getDouble("nameBoost"),
      config.getDouble("contentBoost"),
      config.getDouble("exactAuthorBoost"),
      config.getDouble("exactTitleBoost"))
}
