package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.util.Date;
import java.util.List;

import com.github.sardine.Sardine;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.activation.DataSource;

/**
 * Immutable representation of the response returned by the Nextcloud full-text
 * search API (<code>GET /apps/fulltextsearch/v1/search</code>).
 * <p>
 * A response bundles one {@link Result} per queried search provider, together
 * with the HTTP-like {@link #status()} code, an echo of the {@link Request} that
 * produced it and the server-side app {@link #version()}.
 *
 * @param result  per-provider search results
 * @param status  status code of the search operation ({@code 1} on success)
 * @param request echo of the parsed request that produced this response
 * @param version version string of the full-text search app on the server
 *
 * @see <a href=
 *      "https://github.com/nextcloud/fulltextsearch/wiki/Include-Full-text-search-in-your-client">Nextcloud
 *      full-text search client documentation</a>
 */
@RegisterForReflection
public record FulltextSearchResult(
        List<Result> result,
        int status,
        Request request,
        String version) {

    /**
     * Search results contributed by a single search provider.
     *
     * @param provider  the provider that produced these results
     * @param platform  the platform (client) the search was issued from
     * @param documents the matching documents, ordered by relevance
     * @param info      free-text informational messages from the provider
     * @param meta      aggregate statistics about this provider's search run
     */
    public record Result(
            Provider provider,
            Platform platform,
            List<Document> documents,
            List<String> info,
            Meta meta) {
        /**
         * Identifies a Nextcloud full-text search provider.
         *
         * @param id   unique provider id (e.g. {@code files})
         * @param name human-readable provider name
         */
        public record Provider(String id, String name) {
        }

        /**
         * Identifies the platform/client that issued the search.
         *
         * @param id   unique platform id
         * @param name human-readable platform name
         */
        public record Platform(String id, String name) {
        }

        /**
         * Aggregate statistics for a single provider's search run.
         *
         * @param timedOut whether the search timed out before completing
         * @param time     search execution time in milliseconds
         * @param count    number of documents returned in this response
         * @param total    total number of documents matching the query
         * @param maxScore highest relevance score among the returned documents
         */
        public record Meta(boolean timedOut, int time, int count, int total, int maxScore) {
        }

        /**
         * A single document matched by a full-text search provider. For the
         * {@code files} provider this represents a file or folder in a user's
         * Nextcloud storage.
         *
         * @param id          provider-specific document id (the numeric file id
         *                    for the {@code files} provider)
         * @param providerId  id of the provider that returned this document
         * @param access      ownership and sharing information for the document
         * @param modifiedTime last modification time as a Unix timestamp
         *                    (seconds)
         * @param title       display title of the document
         * @param link        relative link to open the document in Nextcloud
         * @param index       name of the search index the document is stored in
         * @param source      source identifier of the document
         * @param info        file-system metadata for the document
         * @param hash        content hash of the indexed document
         * @param contentSize size of the indexed content in bytes
         * @param tags        tag ids assigned to the document
         * @param metatags    meta-tag ids assigned to the document
         * @param subtags     sub-tag ids assigned to the document
         * @param more        additional provider-specific string values
         * @param excerpts     highlighted text snippets showing where the query
         *                    matched
         * @param score       relevance score of the document, formatted as a
         *                    string
         */
        public record Document(
                String id,
                String providerId,
                Access access,
                long modifiedTime,
                String title,
                String link,
                String index,
                String source,
                Info info,
                String hash,
                long contentSize,
                List<String> tags,
                List<String> metatags,
                List<String> subtags,
                List<String> more,
                List<Excerpt> excerpts,
                String score

        ) {
            /**
             * Ownership and sharing information for a matched document.
             *
             * @param ownerId  user id of the document owner
             * @param viewerId user id of the account that issued the search
             * @param users    user ids the document is shared with
             * @param groups   group ids the document is shared with
             * @param circles  circle ids the document is shared with
             * @param links    public share link tokens for the document
             */
            public record Access(String ownerId, String viewerId, List<String> users, List<String> groups,
                    List<String> circles, List<String> links) {
            }

            /**
             * File-system metadata for a matched document.
             *
             * @param webdav      WebDAV URL of the file
             * @param path        path of the file relative to the user's files
             *                    root
             * @param type        entry type ({@code file} or {@code dir})
             * @param file        file name including extension
             * @param dir         parent directory path
             * @param mime        MIME type of the file
             * @param favorite    whether the user marked the file as a favourite
             * @param size        file size in bytes
             * @param mtime       last modification time as a Unix timestamp
             *                    (milliseconds)
             * @param etag        current ETag of the file
             * @param permissions Nextcloud permission bitmask for the viewer
             * @param unified     thumbnail and icon information
             */
            public record Info(String webdav, String path, String type, String file, String dir, String mime,
                    boolean favorite, long size, long mtime, String etag, int permissions, Unified unified) {
                /**
                 * Thumbnail and icon references for a matched document.
                 *
                 * @param thumbUrl URL of a preview thumbnail, or empty if none
                 *                 is available
                 * @param icon     name/URL of the fallback file-type icon
                 */
                public record Unified(String thumbUrl, String icon) {
                }
            }

            /**
             * A highlighted text snippet showing where the search query matched
             * within a document.
             *
             * @param source  the document field the excerpt was taken from
             * @param excerpt the excerpt text, with matches wrapped in
             *                highlight markup
             */
            public record Excerpt(String source, String excerpt) {
            }

            /**
             * Converts this search hit into a {@link NextcloudFile} handle that
             * can be used to download the document's content over WebDAV.
             *
             * @param server  base URL of the Nextcloud server, prepended to the
             *                document path
             * @param sardine authenticated Sardine WebDAV client used to access
             *                the file content
             * @return a {@link NextcloudFile} pointing at this document
             */
            public NextcloudFile toNextCloudFile(final String server, final Sardine sardine) {
                final String user = access().viewerId();
                final String etag = info().etag();
                final String contentType = info().mime();
                final Date modified = new Date(info().mtime());
                final Long contentLength = (long) info().size();
                final Integer fileId = Integer.parseInt(id());
                final String path = String.format("%s%s", server, info().path());
                final DataSource ds = new SardineDataSource(sardine, path, contentType);
                return new NextcloudFile(fileId, user, path, etag, modified, ds, contentLength);
            }
        }
    }

    /**
     * Echo of the search request as parsed by the server, useful for debugging
     * and for reconstructing paging state.
     *
     * @param providers    ids of the providers the search was dispatched to
     * @param author       user id of the account that issued the search
     * @param search       the raw search string
     * @param empty_search whether the search string was empty (browse mode)
     * @param page         requested result page (1-based)
     * @param size         requested number of results per page
     * @param parts        specific document parts the search was restricted to
     * @param queries      individual parsed query tokens
     * @param options      provider-specific search options that were applied
     * @param metatags     meta-tags the search was filtered by
     * @param subtags      sub-tags the search was filtered by
     * @param tags         tags the search was filtered by
     */
    public record Request(
            List<String> providers,
            String author,
            String search,
            boolean empty_search,
            int page,
            int size,
            List<String> parts,
            List<String> queries,
            Options options,
            List<String> metatags,
            List<String> subtags,
            List<String> tags

    ) {
        /**
         * Provider-specific options for the {@code files} search provider.
         *
         * @param files_local     filter on locally stored files
         * @param files_external  filter on files on external storage
         * @param files_extension filter on a specific file extension
         */
        public record Options(String files_local, String files_external, String files_extension) {
        }
    }

}
