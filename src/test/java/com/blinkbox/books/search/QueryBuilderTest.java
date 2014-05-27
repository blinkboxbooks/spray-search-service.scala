package com.blinkbox.books.search;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.blinkbox.books.search.QueryBuilder.Operator;

public class QueryBuilderTest {
    private QueryBuilder builder;

    @Before
    public void before() {
        builder = new QueryBuilder(Operator.OR, true);
    }

    @Test
    public void append() {
        builder.append("one", "a");
        builder.append("two", "b");
        assertEquals("one:(a) OR two:(b)", builder.toString());
    }

    @Test
    public void appendAndOperator() {
        builder = new QueryBuilder(Operator.AND, false);
        builder.append("one", "a");
        builder.append("two", "b");
        assertEquals("one:a AND two:b", builder.toString());
    }

    @Test
    public void appendBoosted() {
        builder.append("field", "term", 42);
        assertEquals("field:(term)^42.0", builder.toString());
    }

    @Test
    public void appendSubQuery() {
        // Create another AND query clause
        final QueryBuilder other = new QueryBuilder(Operator.AND, true);
        other.append("two", "b");
        other.append("three", "c");

        // Check is added as sub-query
        builder.append("one", "a");
        builder.append(other);
        assertEquals("one:(a) OR ( two:(b) AND three:(c) )", builder.toString());
    }

    @Test
    public void buildSimpleQuery() {
        assertEquals("field:value", QueryBuilder.build("field", "value").toString());
    }

    @Test
    public void appendWildcard() {
        builder.append("field", "term", true);
        assertEquals("field:(term*)", builder.toString());
    }
}
