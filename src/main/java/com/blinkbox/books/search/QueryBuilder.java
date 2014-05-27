package com.blinkbox.books.search;

/**
 * SOLR query string builder.
 * 
 * @author Chris
 */
public class QueryBuilder {
    /**
     * Query clause operators.
     */
    public static enum Operator {
        AND, OR,
    }

    private static final char SPACE = ' ';
    private static final char OPEN_BRACKET = '(';
    private static final char CLOSE_BRACKET = ')';
    private static final char WILD_CARD = '*';

    /**
     * Helper - Creates a simple SOLR query.
     * 
     * @param field
     *            SOLR field
     * @param value
     *            Value
     * @return SOLR query
     */
    public static QueryBuilder build(String field, String value) {
        final QueryBuilder b = new QueryBuilder();
        b.append(field, value);
        return b;
    }

    private final StringBuilder sb = new StringBuilder();
    private final Operator op;
    private final boolean wrap;

    /**
     * Default query builder.
     */
    public QueryBuilder() {
        this(Operator.OR, false);
    }

    /**
     * Constructor.
     * 
     * @param op
     *            Operator
     * @param wrap
     *            Whether to wrap search term in brackets
     */
    public QueryBuilder(Operator op, boolean wrap) {
        if (op == null)
            throw new IllegalArgumentException();
        this.op = op;
        this.wrap = wrap;
    }

    /**
     * Starts a new query clause.
     */
    private void addClause() {
        if (sb.length() > 0) {
            sb.append(SPACE);
            sb.append(op.name());
            sb.append(SPACE);
        }
    }

    public QueryBuilder append(String field, String query) {
        return append(field, query, false);
    }

    /**
     * Adds a clause. Multiple clauses are separated by the <tt>OR</tt> operator.
     * 
     * @param field
     *            Field to search
     * @param query
     *            Query term
     * @param wildcard
     *            Whether this is a wildcard query, default is <tt>false</tt>
     */
    public QueryBuilder append(String field, String query, boolean wildcard) {
        if (query.endsWith("s") || query.contains("s ")) {
            // Tokenize query and insert apostrophes
            final StringBuilder str = new StringBuilder();
            for (String word : query.split(" ")) {
                if (str.length() > 0)
                    str.append(SPACE);
                if (word.endsWith("s")) {
                    str.append(word.substring(0, word.length() - 1));
                    str.append("'s");
                } else {
                    str.append(word);
                }
            }

            // Search for both mangled and original terms
            final QueryBuilder b = new QueryBuilder(Operator.OR, this.wrap);
            b.appendInternal(field, str.toString(), wildcard);
            b.appendInternal(field, query, wildcard);
            append(b);
        } else {
            appendInternal(field, query, wildcard);
        }
        return this;
    }

    private void appendInternal(String field, String query, boolean wildcard) {
        // Add clause delimiter
        addClause();

        // Add search field
        sb.append(field);
        sb.append(':');

        // Add search term
        if (wrap)
            sb.append(OPEN_BRACKET);
        sb.append(query);
        if (wildcard)
            sb.append(WILD_CARD);
        if (wrap)
            sb.append(CLOSE_BRACKET);
    }

    /**
     * Adds a boosted clause (for a boost value greater than one).
     * 
     * @param field
     *            Field to search
     * @param query
     *            Query term
     * @param boost
     *            Boost value 1..n
     */
    public QueryBuilder append(String field, String query, double boost) {
        if (field.contains("exact")) {
            query = "\"" + query + "\"";
        }
        // Add field query
        append(field, query);
        // Append boost term
        if (boost > 1) {
            sb.append('^');
            sb.append(boost);
        }
        return this;
    }

    /**
     * Adds a sub-query (delimited by brackets).
     * 
     * @param b
     *            Sub-query
     */
    public QueryBuilder append(QueryBuilder b) {
        addClause();
        sb.append(OPEN_BRACKET);
        sb.append(SPACE);
        sb.append(b.toString());
        sb.append(SPACE);
        sb.append(CLOSE_BRACKET);
        return this;
    }

    public QueryBuilder append(String str) {
        sb.append(str);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
