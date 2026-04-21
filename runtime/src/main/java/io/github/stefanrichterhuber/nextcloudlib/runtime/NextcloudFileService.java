package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineException;
import com.github.sardine.model.Multistatus;
import com.github.sardine.report.SardineReport;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.ByteArrayDataSource;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FileQueryResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FulltextSearchQuery;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FulltextSearchResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextCloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SardineDataSource;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Condition;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.FileSelector;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Order;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Property;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Query;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.FileSelector.FilterRule;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.activation.DataSource;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

@ApplicationScoped
public class NextcloudFileService {
    @Inject
    Logger logger;

    @Inject
    Sardine sardine;

    @Inject
    NextcloudAuthProvider authProvider;

    public class NextCloudFileLock implements AutoCloseable {
        private final String token;
        private final String url;

        private NextCloudFileLock(String url, String token) {
            this.token = token;
            this.url = url;

        }

        @Override
        public void close() {
            try {
                NextcloudFileService.this.sardine.unlock(url, token);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public String token() {
            return token;
        }

        public String url() {
            return url;
        }

        public NextCloudFile getFile() throws IOException {
            return NextcloudFileService.this.getFile(url());
        }

    }

    /**
     * Get the sanitized WebDav file path for the given user and file
     * 
     * @param path Relative file path
     * @return
     */
    private String getWebDavFilePath(String path) {
        final String user = this.getCurrentUser();

        if (path == null || path.isBlank()) {
            final String target = String.format("%s/remote.php/dav/files/%s", authProvider.getServer(), user);
            return target;
        } else if (path.startsWith(String.format("%s/remote.php/dav/files/%s", authProvider.getServer(), user))) {
            return path;
        } else if (path.startsWith(String.format("%s/remote.php/dav/versions/", authProvider.getServer()))) {
            return path;
        } else {
            // Replace leading /
            path = path.startsWith("/") ? path.substring(1) : path;
            path = path.replace(" ", "%20");
            final String target = String.format("%s/remote.php/dav/files/%s/%s", authProvider.getServer(), user, path);
            return target;
        }
    }

    /**
     * Returns the current user - necessary to build the correct path for all
     * further operations
     * 
     * @return User name
     */
    private String getCurrentUser() {
        return authProvider.getUser();
    }

    /**
     * Downloads the file
     * 
     * @param path Relative file path
     * @return {@link NextCloudFile} found, or null if file does not exists
     * @throws IOException
     */
    public NextCloudFile getFile(@Nullable String path) throws IOException {
        final String target = getWebDavFilePath(path);
        final List<NextCloudFile> results = getFileByInternalPath(target);

        final NextCloudFile result = results.isEmpty() ? null : results.get(0);
        return result;
    }

    /**
     * Returns a file by its <br>
     * internal</br>
     * path (could be the file itself, the file by its id or file version(s))
     * 
     * @param target Internal target path
     * @return List of files found
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html">https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html</a>
     */
    private List<NextCloudFile> getFileByInternalPath(@Nonnull String target) throws IOException {
        final Set<QName> properties = Set.of( //
                new QName("http://owncloud.org/ns", "fileid", "oc"), //
                new QName("DAV:", "getetag", "d"), //
                new QName("DAV:", "getlastmodified", "d"), //
                new QName("DAV:", "getcontentlength", "d"), //
                new QName("DAV:", "getcontenttype", "d") //
        );
        final List<DavResource> propfind = this.sardine.propfind(target, 1, properties);
        final List<NextCloudFile> result = propfind.stream().map(this::davResourceToNextCloudFile).toList();
        return result;
    }

    /**
     * Utility method to convert da DavResource to a NextCloudFile
     * 
     * @param davResource
     * @return
     */
    private NextCloudFile davResourceToNextCloudFile(@Nonnull DavResource davResource) {
        if (davResource != null) {
            final String user = this.getCurrentUser();
            final String etag = davResource.getEtag();
            final String contentType = davResource.getContentType();
            final Date modified = davResource.getModified();
            final Long contentLength = davResource.getContentLength();
            final Integer fileId = Optional.ofNullable(davResource.getCustomProps().get("fileid"))
                    .filter(str -> !str.isBlank())
                    .map(Integer::parseInt).orElse(null);
            final String path = String.format("%s%s", authProvider.getServer(), davResource.getHref().toString());
            final DataSource ds = new SardineDataSource(this.sardine, path, contentType);

            final String filePath = path.replace(getWebDavFilePath(null) + "/", "");
            return new NextCloudFile(fileId, user, filePath, etag, modified, ds, contentLength);
        } else {
            return null;
        }
    }

    /**
     * Returns all reviions of the file with the given path
     * 
     * @param path Path of the file
     * @return List of revisions as NextCloudFile
     */
    public List<NextCloudFile> listFileRevisions(@Nonnull String path) throws IOException {
        final NextCloudFile latest = getFile(path);
        if (latest != null) {
            // For some strange reasone the latest file revision could not be downloaded
            // from the list of revisions -> download by filename
            List<NextCloudFile> result = listFileRevisions(latest.fileId());
            final Date latestRev = result.stream().filter(f -> f.modified() != null).map(f -> f.modified())
                    .max(Date::compareTo)
                    .orElse(null);
            if (latestRev != null) {
                return result.stream().map(file -> {
                    if (Objects.equals(file.modified(), latestRev)) {
                        // Replace with latest file revision to ensure that the content is available for
                        // the latest revision
                        return latest;
                    } else {
                        return (NextCloudFile) file;
                    }
                }).toList();
            } else {
                return result;
            }
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Returns all revisions of the file with the given id
     * 
     * @param fileId ID of the file
     * @return List of revisions as NextCloudFile
     */
    private List<NextCloudFile> listFileRevisions(final int fileId) throws IOException {
        final String user = this.getCurrentUser();
        final String target = String.format("%s/remote.php/dav/versions/%s/versions/%d", authProvider.getServer(), user,
                fileId);

        List<NextCloudFile> result = getFileByInternalPath(target);
        return result;
    }

    /**
     * Gets the content of a file revision (== etag)
     * 
     * @param fileId     ID of the file
     * @param revisionId ID of the reviion
     * @return
     */
    public NextCloudFile getFileRevision(long fileId, @Nonnull String revisionId) throws IOException {
        final String user = this.getCurrentUser();
        final String target = String.format("%s/remote.php/dav/versions/%s/versions/%d/%s", authProvider.getServer(),
                user,
                fileId, revisionId);
        final List<NextCloudFile> results = getFileByInternalPath(target);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Gets the content of a file revision (== etag)
     * 
     * @param path       Path of the file
     * @param revisionId ID of the reviion. If null, the latest file revision is
     *                   returned
     * @return
     */
    public NextCloudFile getFileRevision(@Nonnull String path, @Nullable String revisionId) throws IOException {
        final NextCloudFile latest = getFile(path);
        if (revisionId == null || revisionId.isBlank()) {
            return latest;
        }
        if (latest != null) {
            return getFileRevision(latest.fileId(), revisionId);
        } else {
            return null;
        }
    }

    /**
     * Creates a new NextCloudFile with a the file content already downloaded
     * instead of being laizly available
     * 
     * @param file File to download
     * @return NextCloudFile with the content downloaded, or null if the given file
     *         was null
     * @throws IOException
     */
    public NextCloudFile downloadFileImmediately(NextCloudFile file) throws IOException {
        if (file == null) {
            return null;
        }
        String etag = file.etag();
        String contentType = file.dataSource().getContentType();
        Date modDate = file.modified();
        // Really ensure that the content of the file revision we fetched earlier is
        // downloaded
        Map<String, String> headers = new HashMap<>();

        if (file.path().startsWith(String.format("%s/remote.php/dav/versions/", authProvider.getServer()))) {
            // If we download a file version, no need to use etags, because the file content
            // cannot be changed anymore -> no need to use etags to ensure that the content
            // is still the same as when we fetched the metadata

        } else {
            if (etag != null) {
                headers.put("If-Match", etag);
            }
        }
        try (InputStream is = this.sardine.get(getWebDavFilePath(file.path()), headers)) {
            byte[] content = is.readAllBytes();
            ByteArrayDataSource ds = new ByteArrayDataSource(file.path(), contentType, content);
            return new NextCloudFile(file.fileId(), contentType, file.path(), etag, modDate, ds,
                    file.contentLength());
        }
    }

    /**
     * Returns the File revision for a given revision date. Files are cached for
     * better performance
     * 
     * @param path             File path
     * @param modificationDate Modification date
     * @return File found
     */
    @Retry(maxRetries = 4)
    public NextCloudFile getFileByModifyDate(@Nonnull String path, @Nullable Date modificationDate) throws IOException {
        if (modificationDate == null || modificationDate.getTime() == 0) {
            // Load latest revision
            final NextCloudFile result = this.getFile(path);
            return result;
        } else {
            // Sorted by revision date
            final List<NextCloudFile> revisions = this.listFileRevisions(path).stream()
                    .filter(rev -> rev.modified() != null)
                    .sorted((r1, r2) -> r1.modified().compareTo(r2.modified())).toList();
            // Find the revision with the the given modification date
            NextCloudFile found = revisions.stream().filter(f -> modificationDate.equals(f.modified()))
                    .findFirst()
                    .orElse(null);

            return found;
        }
    }

    /**
     * List all files in the given path
     * 
     * @param path  Relative file path. Null for root dir
     * @param depth List depth. -1 for infinite recursion
     * @return List of Nextcloud files found
     * @throws IOException
     */
    public List<NextCloudFile> listFiles(@Nullable String path, int depth)
            throws IOException {
        final Set<QName> qproperties = Set.of( //
                new QName("http://owncloud.org/ns", "fileid", "oc"), //
                new QName("DAV:", "getetag", "d"), //
                new QName("DAV:", "getlastmodified", "d"), //
                new QName("DAV:", "getcontentlength", "d"), //
                new QName("DAV:", "getcontenttype", "d"), //
                new QName("DAV:", "displayname", "d") //
        );

        final String target = getWebDavFilePath(path);
        final List<DavResource> propfind = this.sardine.propfind(target, depth, qproperties);

        final List<NextCloudFile> result = propfind.stream().map(this::davResourceToNextCloudFile).toList();
        return result;
    }

    /**
     * List all files in the given path with the given selector applied
     * 
     * @param path  Relative file path. Null for root dir
     * @param depth List depth. -1 for infinite recursion
     * @param rules List of rules to apply
     * @return List of Nextcloud files found
     * @throws IOException
     */
    public List<NextCloudFile> listFiles(@Nullable String path, int depth, List<FilterRule> rules) throws IOException {
        if (rules == null || rules.isEmpty()) {
            return listFiles(path, depth);
        }

        FileSelector selector = FileSelector.list(Property.FILE_ID, Property.GET_ETAG, Property.GET_CONTENT_TYPE,
                Property.GET_LAST_MODIFIED, Property.GET_CONTENT_LENGTH);
        for (final FilterRule rule : rules) {
            selector = selector.withFilter(rule.property(), rule.value());
        }
        final FileQueryResult fqr = listFiles(path, depth, selector);
        if (fqr != null) {
            final List<NextCloudFile> result = fqr.getFiles().stream()
                    .map(f -> f.toNextCloudFile(sardine, this.getCurrentUser())).toList();
            return result;
        } else {
            return Collections.emptyList();
        }

    }

    /**
     * List alle files in the given path with the given selector applied
     * 
     * @param path     Relative file path. Null for root dir
     * @param selector Properties to select and additional filter conditions to
     *                 apply
     * @param depth    List depth. -1 for infinite recursion
     * @return FileQueryResult with the result
     * @throws IOException
     */
    public FileQueryResult listFiles(@Nullable String path, int depth, @Nonnull FileSelector selector)
            throws IOException {
        final String target = getWebDavFilePath(path);

        final FileQueryResult result = sardine.report(target, depth, new SardineReport<FileQueryResult>() {

            @Override
            public String toXml() throws IOException {
                return selector.toXML();
            }

            @Override
            public Object toJaxb() {
                return null;
            }

            @Override
            public FileQueryResult fromMultistatus(Multistatus multistatus) {
                return FileQueryResult.of(multistatus);
            }
        });
        return result;
    }

    /**
     * Performs a search operation on the file server
     * 
     * @param query Search query to execute, must not be null
     * @return FileQueryResult containing the result of the search
     * @see <a href=
     *      "https://docs.nextcloud.com/server/19/developer_manual/client_apis/WebDAV/search.html">https://docs.nextcloud.com/server/19/developer_manual/client_apis/WebDAV/search.html</a>
     */
    public FileQueryResult search(@Nonnull Query query) throws IOException {
        final String fromPrefix = String.format("/files/%s/", this.getCurrentUser());

        // For convience set a from as user root folder, if not set
        if (query.getFrom() == null || query.getFrom().isBlank()) {
            final List<Property> select = query.getSelect();
            final String from = fromPrefix;
            final Condition condition = query.getWhere();
            final List<Order> order = query.getOrderBy();
            final Integer limit = query.getLimit();

            Query q = Query.select(select).from(from).where(condition).orderBy(order);
            if (limit != null) {
                q = q.limit(limit);
            }

            return search(q);
        }

        // Check if query valid
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(query.getWhere(), "query.where must not be null");
        Objects.requireNonNull(query.getFrom(), "query.from must not be null");

        // At the moment only search queries for files are supported meaning the the
        // scope should always start with files/$username.
        if (!query.getFrom().startsWith(fromPrefix)) {
            logger.errorf("Search queries must start with '/files/$username/: %s --> Prefix is added", query.getFrom());

            final List<Property> select = query.getSelect();
            final String from = fromPrefix
                    + (query.getFrom().startsWith("/") ? query.getFrom().substring(1) : query.getFrom());
            final Condition condition = query.getWhere();
            final List<Order> order = query.getOrderBy();
            final Integer limit = query.getLimit();

            Query q = Query.select(select).from(from).where(condition).orderBy(order);
            if (limit != null) {
                q = q.limit(limit);
            }

            return search(q);
        }
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        logger.debugf("Requested search: %s", query);
        final String result = client.search(query);

        try {
            final SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            final SAXSource saxSource = new SAXSource(
                    spf.newSAXParser().getXMLReader(),
                    new InputSource(new StringReader(result)));
            final JAXBContext context = JAXBContext.newInstance(Multistatus.class);
            final Multistatus status = (Multistatus) context.createUnmarshaller().unmarshal(saxSource);

            return FileQueryResult.of(status);
        } catch (JAXBException | ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
    }

    /**
     * If the full-text search module is installed, one can perform fulltext-search
     * queries
     * 
     * @param query Search query to execute
     * @return result of the search query
     */
    public FulltextSearchResult fulltextSearch(final FulltextSearchQuery query) {
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        final FulltextSearchResult result = client.fulltextsearch(query);
        return result;
    }

    /**
     * Upload the file
     * 
     * @param path    Relative path of the file
     * @param content Content to upload
     * @throws IOException
     */
    public void uploadFile(String path, @Nonnull InputStream content) throws IOException {
        final String url = getWebDavFilePath(path);
        sardine.put(url, content);
    }

    /**
     * Upload the file
     * 
     * @param path        Relative path of the file
     * @param contentType Content type of the file
     * @param content     Content to upload
     * @throws IOException
     */
    public void uploadFile(String path, @Nullable String contentType, @Nonnull InputStream content) throws IOException {
        final String url = getWebDavFilePath(path);
        sardine.put(url, content, contentType);
    }

    /**
     * Upload the file
     * 
     * @param path        Relative path of the file
     * @param contentType Optional content type of the file
     * @param content     Content to upload
     * @param etag        Optional etag of the original content. If set, file upload
     *                    only
     *                    succeeds if ETAG matches ( file unchanged )
     * @param lockToken   Optional Lock token to transmit to indicate we
     *                    handle this upload within an existing lock
     * @throws IOException
     */
    @SuppressWarnings("unused")
    public void uploadFile(String path, @Nullable String contentType, @Nonnull InputStream content,
            @Nullable String etag,
            @Nullable String lockToken)
            throws IOException {
        final String url = getWebDavFilePath(path);
        final Map<String, String> headers = new HashMap<>();
        if (etag != null) {
            headers.put("If-Match", etag);
        }
        if (lockToken != null && false) {
            // FIXME: Does not work currently with nextcloud
            headers.put("If", String.format("</remote.php/dav/files/%s/%s> (<%s> [%s])", authProvider.getUser(), path,
                    lockToken, etag));
        }
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        sardine.put(url, content, headers);
    }

    /**
     * Upload the file
     * 
     * @param path        Relative path of the file
     * @param contentType Optional content type of the file
     * @param content     Content to upload
     * @param etag        Optional etag of the original content. If set, file upload
     *                    only
     *                    succeeds if ETAG matches ( file unchanged )
     * @param lock        Optional Lock to transmit to indicate we
     *                    handle this upload within an existing lock
     * @throws IOException
     */
    public void uploadFile(String path, @Nullable String contentType, @Nonnull InputStream content,
            @Nullable String etag,
            @Nullable NextCloudFileLock lock)
            throws IOException {
        final String lockTocken = lock != null ? lock.token() : null;
        uploadFile(path, contentType, content, etag, lockTocken);
    }

    /**
     * Deletes the file
     * 
     * @param path      Relative path of the file
     * @param etag      Optional etag of the original content. If set, file delete
     *                  only
     *                  succeeds if ETAG matches ( file unchanged )
     * @param lockToken Optional Lock token to transmit to indicate we
     *                  handle this delete within an existing lock
     * @throws IOException
     */
    public void deleteFile(String path, @Nullable String etag, @Nullable String lockToken)
            throws IOException {
        final String url = getWebDavFilePath(path);
        final Map<String, String> headers = new HashMap<>();
        if (etag != null) {
            headers.put("If-Match", etag);
        }
        if (lockToken != null) {
            headers.put("If", String.format("(<%s>)", lockToken));
        }
        sardine.delete(url, null);
    }

    /**
     * Deletes the file
     * 
     * @param path Relative path of the file
     * @param etag Optional etag of the original content. If set, file delete
     *             only
     *             succeeds if ETAG matches ( file unchanged )
     * @param lock Optional Lock to transmit to indicate we
     *             handle this delete within an existing lock
     * @throws IOException
     */
    public void deleteFile(String path, @Nullable String etag, @Nullable NextCloudFileLock lock)
            throws IOException {
        final String lockTocken = lock != null ? lock.token() : null;
        deleteFile(path, etag, lockTocken);
    }

    /**
     * Moves a file from one destination to the other. File id stays the same!
     * 
     * @param path       Relative source path of the file (including filename!)
     * @param targetPath Relative target path of the file (including filename!)
     */
    public void moveFile(String path, String targetPath) throws IOException {
        final String src = getWebDavFilePath(path);
        final String target = getWebDavFilePath(targetPath);
        sardine.move(src, target);
    }

    /**
     * Creates a directory at the given path. If parent directories do not exist,
     * they are created as well
     * 
     * @param path
     */
    public void createDirectory(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be null or blank");
        }
        if ("/".equals(path)) {
            throw new IllegalArgumentException("Cannot create directory at root path");
        }
        final String url = getWebDavFilePath(path);
        sardine.createDirectory(url);
    }

    /**
     * Creates a directoy at the given path. If parent directories do not exist,
     * they are created as well. If the directory already exists, nothing happens
     * 
     * @param path
     */
    public void createDirectories(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return;
        }
        if ("/".equals(path)) {
            return;
        }
        try {
            createDirectory(path);
        } catch (IOException e) {
            if (e instanceof SardineException ioException) {
                if (ioException.getStatusCode() == 405) {
                    // Directory already exists, ignore
                    return;
                }
            }
            throw e;
        }
    }

    /**
     * Locks the given file remote on the Nextcloud server. Requires the
     * 'files_lock' app to be active
     * 
     * @param path relative path of file
     * @return {@link NextCloudFileLock}
     * @throws RuntimeException
     */
    public NextCloudFileLock lockFile(String path) throws IOException {
        final String url = getWebDavFilePath(path);
        final String token = sardine.lock(url);
        return new NextCloudFileLock(url, token);
    }

}
