Search Service
==============

This is a service that provides a simple API for searching for books, implemented using Scala and Spray, using book metadata stored in Solr.

## Features

The service supports:

- Free text search for books.
- Incremental search for books and authors, for as-you-type suggestions in a search field.
- Finding books that are similar to a given book.
- Suggest corrected spellings for search terms.

## API

### Search for books

URL: /search/suggestions

Parameter    | Type          | Default      | Description
------------ | ------------- | ------------ | -----------------
q            | String        | -            | Search query
offset       | Integer       | 0            | Index of first result to return, for pagination
count        | Integer       | 20           | Number of results to return

Example response:

```json
{
  "type": "urn:blinkboxbooks:schema:search",
  "id": "dickens",
  "links": [
    {
      "rel": "this",
      "href": "http://search.service/search/books?q=dickens&offset=0"
    },
    {
      "rel": "next",
      "href": "http://search.service/search/books?q=dickens&offset=50&count=50"
    }
  ],
  "numberOfResults": "310",
  "books": [
    {
      "id": "9780141974132",
      "title": "Nicholas Nickleby",
      "authors": [
        "Charles Dickens"
      ]
    },
    {
      "id": "9780141974149",
      "title": "Our Mutual Friend",
      "authors": [
        "Charles Dickens"
      ]
    }
  ]
}
```

### Auto-complete suggestions

URL: /search/suggestions

This will perform a search and return suggested books and authors. The query that's run will use the given query string as a prefix of potential matches, i.e. assume that the string is not a complete query but what a user has typed so far.

Parameter    | Type          | Default      | Description
------------ | ------------- | ------------ | -----------------
q            | String        | -            | Search query
count        | Integer       | 20           | Number of results to return

Example response:

```json
{
  "items": [
    {
      "type": "urn:blinkboxbooks:schema:suggestion:book",
      "title": "Italo Calvino's Architecture of Lightness",
      "id": "9781136730597",
      "authors": [
        "Letizia Modena"
      ]
    },
    {
      "type": "urn:blinkboxbooks:schema:suggestion:contributor",
      "title": "If on a Winter's Night a Traveler",
      "id": "9780156439619",
      "authors": [
        "Italo Calvino"
      ]
    }
  ]
}```

### Similar books

URL: /search/books/{id}/similar

This endpoint will use Solr's "More Like This" functionality to return books that are similar to a specified one.

The `id` is the ISBN of the book for which we want to find similar books.

Parameter    | Type          | Default      | Description
------------ | ------------- | ------------ | -----------------
offset       | Integer       | 0            | Index of first result to return, for pagination
count        | Integer       | 20           | Number of results to return

Example response:

```json
{
  "type": "urn:blinkboxbooks:schema:search:similar",
  "id": "9781451685626",
  "links": [
    {
      "rel": "this",
      "href": "http://search.service/search/books/9781451685626/similar?offset=0"
    },
    {
      "rel": "next",
      "href": "http://search.service/search/books/9781451685626/similar?offset=10"
    }
  ],
  "numberOfResults": "310",
  "books": [
    {
      "id": "9780141974132",
      "title": "Nicholas Nickleby",
      "authors": [
        "Charles Dickens"
      ]
    },
    {
      "id": "9780141974149",
      "title": "Our Mutual Friend",
      "authors": [
        "Charles Dickens"
      ]
    }
  ]
}
```

## Configuring Solr

This repository contains the configuration used to set up Solr, in the `src/main/resources/` folder. These files configure a Solr instance with a single core called `books`, and provides the schema for this.

The schema (in `schema.xml`) defines the metadata fields used on documents that represent books, as well as the tokenizers used to process text field.

The `solrconfig.xml` file contains definitions of the query handlers used for search and "more like this" queries, and the spell checker.

## Building and running

The Search Service builds as a standalone Jar file using `sbt`.

It uses the common Blinkbox Books conventions and approaches to configuration, metrics, health endpoints etc., see [the common-config library](https://github.com/blinkboxbooks/common-config.scala) for details.

See the [application.conf](/src/main/resources/application.conf) file for properties that need to be provided, and [reference.conf](/src/main/resources/reference.conf) for settings that can optionally be overridden.

## Tests

As well as a suite of unit tests, the service has a set of functional tests that are run in the same way as the unit tests, using ScalaTest (i.e. run via `sbt run`).

The [functional tests](src/test/scala/com/blinkbox/books/search/SearchServiceFunctionalTests.scala) are of particular interest, as they run against an embedded Solr instance configured using the actual Solr configuration. This ensures that this configuration works as expected, and that it's compatible with the code in the Search Service. 
