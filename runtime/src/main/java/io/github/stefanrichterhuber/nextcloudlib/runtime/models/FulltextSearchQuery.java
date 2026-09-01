package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Immutable representation of a Nextcloud full-text search query, sent to
 * <code>GET /apps/fulltextsearch/v1/search</code> as a JSON request body. The
 * corresponding response is modelled by {@link FulltextSearchResult}.
 * <p>
 * Instances are serialized to JSON by {@link #toString()}, which is the form the
 * JAX-RS client transmits.
 *
 * @param providers id of the search provider to use, or {@code "all"} to query
 *                  every registered provider (the default)
 * @param search    the search string
 * @param page      requested result page, 1-based
 * @param size      number of results per page
 * @param options   provider-specific search options
 *
 * @see <a href=
 *      "https://github.com/nextcloud/fulltextsearch/wiki/Include-Full-text-search-in-your-client">Nextcloud
 *      full-text search client documentation</a>
 */
@RegisterForReflection
public record FulltextSearchQuery(String providers, String search, int page, int size, Options options) {
    /**
     * Provider-specific options for the {@code files} search provider. Each
     * value is a filter expression as understood by Nextcloud; an empty string
     * means "no filter".
     *
     * @param files_local     filter on locally stored files
     * @param files_external  filter on files on external storage
     * @param files_extension filter on a specific file extension
     */
    public record Options(String files_local, String files_external, String files_extension) {
    }

    /**
     * Creates a FulltextSearchQuery with some reasonable defaults
     * 
     * @param query Query string to search for
     * @return FulltextSearchQuery
     */
    public static FulltextSearchQuery search(String query) {
        return search(query, 1, 20);
    }

    /**
     * Creates a FulltextSearchQuery with some reasonable defaults
     * 
     * @param query Query string to search for
     * @param page  search result page
     * @param size  number of results per page
     * @return FulltextSearchQuery
     */
    public static FulltextSearchQuery search(String query, int page, int size) {
        final FulltextSearchQuery q = new FulltextSearchQuery("all", query, page, size, new Options("", "", ""));
        return q;
    }

    /**
     * Serializes this query to its JSON representation. This is invoked by the
     * JAX-RS client to build the request body sent to the full-text search API.
     *
     * @return the query as a JSON document
     * @throws RuntimeException if the query cannot be serialized to JSON
     */
    public String toString() {
        final ObjectMapper om = new ObjectMapper();
        try {
            final String request = om.writeValueAsString(this);
            return request;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
