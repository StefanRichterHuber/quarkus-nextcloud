package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FileQueryResult.QueriedFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FulltextSearchQuery;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FulltextSearchResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SardineDataSource;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SystemTag;
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
    /**
     * {@link JAXBContext} instances are thread-safe and expensive to create, so the
     * one used to unmarshal WebDAV multistatus search responses is built once.
     */
    private static final JAXBContext MULTISTATUS_JAXB_CONTEXT;
    static {
        try {
            MULTISTATUS_JAXB_CONTEXT = JAXBContext.newInstance(Multistatus.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Inject
    Logger logger;

    @Inject
    Sardine sardine;

    @Inject
    NextcloudAuthProvider authProvider;

    /**
     * Requires files_lock app
     */
    @Inject
    @ConfigProperty(name = "nextcloud.file-lock-enabled", defaultValue = "false")
    boolean lockEnabled = false;

    /**
     * Represents a locked Nextcloud file. As an AutoCloseable one can easily use
     * the
     * file with the given helper methods for modification and unlock it
     * afterwards
     * NextCloudFileLock
     */
    public class NextCloudFileLock implements AutoCloseable {
        private String token;
        private final String url;
        private NextcloudFile file = null;

        private NextCloudFileLock(String url, String token) {
            this.token = token;
            this.url = url;
            this.file = null;

        }

        @Override
        public void close() {
            try {
                unlock();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Unlocks this file
         * 
         * @throws IOException
         */
        public void unlock() throws IOException {
            if (token != null) {
                NextcloudFileService.this.sardine.unlock(url, token);
                token = null;
            }
        }

        /**
         * Lock token
         */
        public String token() {
            return token;
        }

        /**
         * URL of this file
         * 
         * @return
         */
        public String url() {
            return url;
        }

        /**
         * Etag of this file
         * 
         * @return
         * @throws IOException
         */
        public String etag() throws IOException {
            return file().etag();
        }

        /**
         * File locked by this lock
         * 
         * @return
         * @throws IOException
         */
        public NextcloudFile file() throws IOException {
            if (file == null) {
                file = NextcloudFileService.this.getFile(url());
            }
            return file;
        }

        /**
         * Uploads new content into this locked file
         * 
         * @param contentType Type of the content to upload (might be null for
         *                    auto-detect)
         * @param content     Content to upload
         * 
         * @throws IOException
         */
        public void uploadContent(String contentType, @Nonnull InputStream content) throws IOException {
            final String etag = etag();
            final String lockTocken = token();
            NextcloudFileService.this.uploadFile(url, contentType, content, etag,
                    lockTocken);
            // Etag becomes invalid, refetch file for current etag!
            this.file = null;
        }

        /**
         * Uploads new content into this locked file
         * 
         * @param content Content to upload
         * 
         * @throws IOException
         */
        public void uploadContent(@Nonnull InputStream content) throws IOException {
            uploadContent(null, content);
        }

        /**
         * Deletes this file. This also unlocks the file!
         * 
         * @throws IOException
         */
        public void deleteFile() throws IOException {
            final String etag = etag();
            final String lockTocken = token();
            NextcloudFileService.this.deleteFile(url, etag, lockTocken);
            // File deleted, neither file handle nor token are any longer valid
            file = null;
            token = null;
        }

        /**
         * Prepares moving this file one destination to the other. File id stays the
         * same!
         * 
         * @throws IOException
         * 
         */
        public FileMoveOperationBuilder moveFile() throws IOException {
            return NextcloudFileService.this.moveFile().from(this);
        }

        /**
         * Prepares coping this file one destination to the other. File id stays the
         * same!
         * 
         * @throws IOException
         * 
         */
        public FileMoveOperationBuilder copyFile() throws IOException {
            return NextcloudFileService.this.copyFile().from(this);
        }

    }

    /**
     * Get the absolute WebDav URL for the given user and file<br>
     * 
     * <ul>
     * <li>Absolute paths to files and file versions are passed through
     * <li>relative-paths to dav (paths starting with '/remote.php/dav/') are
     * prepended with the server url (e.g.
     * '/remote.php/dav/files/admin/documents/test.txt' ->
     * 'https://nextcloud.example.com/remote.php/dav/files/admin/documents/test.txt')
     * <li>Empty / null paths are rewritten to the root folder of the current user:
     * e.g. '' -> 'https://nextcloud.example.com/remote.php/dav/files/admin'
     * <li>Any other path is treated as relative path to the root folder of the
     * current user (e.g. '/documents/test.txt' ->
     * 'https://nextcloud.example.com/remote.php/dav/files/admin/documents/test.txt')
     * </ul>
     * <br>
     * 
     * @param path Relative file path
     * @return Absolute URL of the file
     */
    protected String getWebDavFilePath(String path) {
        final String user = this.getCurrentUser();

        if (path == null || path.isBlank()) {
            // Empty path -> point to the root file folder
            final String target = String.format("%s/remote.php/dav/files/%s", authProvider.getServer(), user);
            return target;
        } else if (path.startsWith(String.format("%s/remote.php/dav/files/", authProvider.getServer()))) {
            // This is an absolute path to a file -> don't touch it
            return path;
        } else if (path.startsWith(String.format("%s/remote.php/dav/versions/", authProvider.getServer()))) {
            // This is an absolut path to a file version -> don't touch it
            return path;
        } else if (path.startsWith("/remote.php/dav/")) {
            // This is a relative path to the dav sub system -> prepend server url
            path = authProvider.getServer() + path;
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
     * @return {@link NextcloudFile} found, or null if file does not exists
     * @throws IOException
     */
    public NextcloudFile getFile(@Nullable String path) throws IOException {
        final String target = getWebDavFilePath(path);
        final List<NextcloudFile> results = getFileByInternalPath(target);

        final NextcloudFile result = results.isEmpty() ? null : results.get(0);
        return result;
    }

    /**
     * Downloads the file by its ID
     * 
     * @param fileId ID of the file
     * @return {@link NextcloudFile} found, or null if file does not exists
     * @throws IOException
     */
    public NextcloudFile getFileById(int fileId) throws IOException {
        final List<NextcloudFile> results = getFilesByFileIds(List.of(fileId));
        final NextcloudFile result = results.isEmpty() ? null : results.get(0);
        return result;
    }

    /**
     * Retrieves a list of Nextcloud files by their file IDs.
     * 
     * @param fileIds the collection of file IDs to retrieve
     * @return the list of Nextcloud files
     * @throws IOException if an error occurs while retrieving the files
     */
    public List<NextcloudFile> getFilesByFileIds(Collection<Integer> fileIds) throws IOException {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        final List<NextcloudFile> files = new ArrayList<>(fileIds.size());

        final Condition condition = Condition.and(Condition.isFile(), fileIds.stream().filter(Objects::nonNull)
                .map(fileId -> Condition.equals(Property.FILE_ID, fileId)).reduce((c1, c2) -> Condition.or(c1, c2))
                .get());

        final FileQueryResult result = search(
                Query.select(Property.DISPLAY_NAME)
                        .where(condition));

        for (QueriedFile file : result.getFiles()) {
            final String path = file.getPath();
            final NextcloudFile nextcloudFile = getFile(path);
            if (nextcloudFile != null) {
                files.add(nextcloudFile);
            }
        }

        return files;
    }

    /**
     * Returns a file by its
     * internal path (could be the file itself, the file by its id or file
     * version(s))
     * 
     * @param target Internal target path
     * @return List of files found
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html">https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html</a>
     */
    protected List<NextcloudFile> getFileByInternalPath(@Nonnull String target) throws IOException {
        final Set<QName> properties = Set.of( //
                new QName("http://owncloud.org/ns", "fileid", "oc"), //
                new QName("DAV:", "getetag", "d"), //
                new QName("DAV:", "getlastmodified", "d"), //
                new QName("DAV:", "getcontentlength", "d"), //
                new QName("DAV:", "getcontenttype", "d") //
        );
        final List<DavResource> propfind = this.sardine.propfind(target, 1, properties);
        final List<NextcloudFile> result = propfind.stream().map(this::davResourceToNextCloudFile).toList();
        return result;
    }

    /**
     * Utility method to convert da DavResource to a NextCloudFile
     * 
     * @param davResource
     * @return
     */
    protected NextcloudFile davResourceToNextCloudFile(@Nonnull DavResource davResource) {
        if (davResource != null) {
            final String user = this.getCurrentUser();
            final String etag = davResource.getEtag();
            final String contentType = davResource.getContentType();
            final Date modified = davResource.getModified();
            final Long contentLength = davResource.getContentLength();
            final String path = getWebDavFilePath(davResource.getHref().toString());
            // Direct file version resources do not carry a 'fileid' property, but the id
            // is part of the path (.../dav/versions/{user}/versions/{fileId}/{versionId})
            final Integer fileId = Optional.ofNullable(davResource.getCustomProps().get("fileid"))
                    .filter(NumberUtils::isParsable)
                    .map(Integer::parseInt)
                    .orElseGet(() -> parseFileIdFromVersionPath(path));
            final DataSource ds = new SardineDataSource(this.sardine, path, contentType);

            final String filePath = path.replace(getWebDavFilePath(null) + "/", "");
            return new NextcloudFile(fileId, user, filePath, etag, modified, ds, contentLength);
        } else {
            return null;
        }
    }

    /**
     * Pattern to extract the file id from an (absolute or relative) WebDav file
     * version path of the form
     * {@code .../remote.php/dav/versions/{user}/versions/{fileId}[/{versionId}]}.
     */
    private static final Pattern VERSION_PATH_PATTERN = Pattern
            .compile(".*/remote\\.php/dav/versions/[^/]+/versions/(\\d+)(?:/(\\d+))?/?");

    /**
     * Extracts the file id from a WebDav file version path.
     *
     * @param path Absolute or relative version path
     * @return File id, or {@code null} if the path is not a version path
     */
    private static Integer parseFileIdFromVersionPath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        final Matcher matcher = VERSION_PATH_PATTERN.matcher(path);
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /**
     * Extracts the version id (the trailing path segment) from a WebDav file
     * version
     * path. Nextcloud names each version by the modification time (in epoch
     * seconds)
     * of the content it holds.
     *
     * @param path Absolute or relative version path
     * @return Version id, or {@code null} if the path carries no version id
     */
    private static Long parseVersionIdFromPath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        final Matcher matcher = VERSION_PATH_PATTERN.matcher(path);
        return matcher.matches() && matcher.group(2) != null ? Long.valueOf(matcher.group(2)) : null;
    }

    /**
     * Returns all revisions of the given file
     *
     * @param srcFile File to read revisions
     * @return List of revisions as NextCloudFile
     */
    public List<NextcloudFile> listFileRevisions(@Nonnull NextcloudFile srcFile) throws IOException {
        // The versions endpoint returns the stored (past) versions of the file.
        // Depending on the server it also includes a synthetic entry for the current
        // version, named by the file's current modification time - but that entry has
        // no downloadable content under the versions path (the current content only
        // lives at the file's real path). Drop that entry (and any entry carrying the
        // current etag) and append srcFile instead, so the current revision is always
        // present exactly once, with working content, as the last (newest) element.
        final Long currentVersionId = srcFile.modified() != null ? srcFile.modified().getTime() / 1000L : null;

        final List<NextcloudFile> revisions = listFileRevisions(srcFile.fileId()).stream()
                // Drop folder placeholders (size -1).
                .filter(f -> f.contentLength() != null && f.contentLength() >= 0)
                .filter(f -> !isCurrentVersionEntry(f, srcFile, currentVersionId))
                .sorted(Comparator.comparing(NextcloudFile::modified,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toCollection(ArrayList::new));

        revisions.add(srcFile);
        return List.copyOf(revisions);
    }

    /**
     * Tells whether a revision returned by the versions endpoint actually
     * represents
     * the current version of the file (rather than a stored past version). Such an
     * entry is either tagged with the file's current etag or named by the file's
     * current modification time.
     *
     * @param entry            Revision entry from the versions endpoint
     * @param srcFile          Current version of the file
     * @param currentVersionId {@code srcFile}'s modification time in epoch seconds,
     *                         or
     *                         {@code null} if unknown
     * @return {@code true} if the entry is the current version
     */
    private static boolean isCurrentVersionEntry(@Nonnull NextcloudFile entry, @Nonnull NextcloudFile srcFile,
            @Nullable Long currentVersionId) {
        if (srcFile.etag() != null && Objects.equals(entry.etag(), srcFile.etag())) {
            return true;
        }
        final Long entryVersionId = parseVersionIdFromPath(entry.path());
        return currentVersionId != null && Objects.equals(entryVersionId, currentVersionId);
    }

    /**
     * Returns all revisions of the file with the given path
     * 
     * @param path Path of the file
     * @return List of revisions as NextCloudFile
     */
    public List<NextcloudFile> listFileRevisions(@Nonnull String path) throws IOException {
        final NextcloudFile latest = getFile(path);
        if (latest != null) {
            return listFileRevisions(latest);
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
    private List<NextcloudFile> listFileRevisions(final int fileId) throws IOException {
        final String user = this.getCurrentUser();
        final String target = String.format("%s/remote.php/dav/versions/%s/versions/%d", authProvider.getServer(), user,
                fileId);

        List<NextcloudFile> result = getFileByInternalPath(target);
        return result;
    }

    /**
     * Gets a stored (past) revision of a file by its version id.
     * <p>
     * {@code revisionId} must be a version id as returned in
     * {@link NextcloudFile#etag()} / the trailing path segment of a revision from
     * {@link #listFileRevisions(NextcloudFile)} - i.e. the modification time in
     * epoch
     * seconds. The current version of a file is not addressable here; use
     * {@link #getFileRevision(String, String)} or {@link #getFile(String)} for
     * that.
     *
     * @param fileId     ID of the file
     * @param revisionId Version id of the revision
     * @return The revision, or {@code null} if the server returned no resource
     * @throws IOException if the revision does not exist or cannot be read
     */
    public NextcloudFile getFileRevision(long fileId, @Nonnull String revisionId) throws IOException {
        final String user = this.getCurrentUser();
        final String target = String.format("%s/remote.php/dav/versions/%s/versions/%d/%s", authProvider.getServer(),
                user,
                fileId, revisionId);
        final List<NextcloudFile> results = getFileByInternalPath(target);
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
    public NextcloudFile getFileRevision(@Nonnull String path, @Nullable String revisionId) throws IOException {
        final NextcloudFile latest = getFile(path);
        if (revisionId == null || revisionId.isBlank()) {
            return latest;
        }
        if (latest == null) {
            return null;
        }
        final String currentVersionId = latest.modified() != null
                ? Long.toString(latest.modified().getTime() / 1000L)
                : null;
        if (revisionId.equals(latest.etag()) || revisionId.equals(currentVersionId)) {
            // The requested revision is the current version of the file. Its content is
            // only downloadable from the file's real path, not from the versions
            // endpoint, so return the live file directly.
            return latest;
        }
        return getFileRevision(latest.fileId(), revisionId);
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
    public NextcloudFile downloadFileImmediately(NextcloudFile file) throws IOException {
        if (file == null) {
            return null;
        }
        if (file.dataSource() instanceof ByteArrayDataSource) {
            return file;
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
            return new NextcloudFile(file.fileId(), contentType, file.path(), etag, modDate, ds,
                    file.contentLength());
        }
    }

    /**
     * Returns the File revision for a given revision date.
     * 
     * @param path             File path
     * @param modificationDate Modification date
     * @return File found
     */
    public NextcloudFile getFileByModifyDate(@Nonnull String path, @Nullable Date modificationDate) throws IOException {
        if (modificationDate == null || modificationDate.getTime() == 0) {
            // Load latest revision
            final NextcloudFile result = this.getFile(path);
            return result;
        } else {
            // Find the revision with the the given modification date
            final NextcloudFile found = this.listFileRevisions(path).stream()
                    .filter(rev -> rev.modified() != null)
                    .filter(f -> modificationDate.equals(f.modified()))
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
    public List<NextcloudFile> listFiles(@Nullable String path, int depth)
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

        final List<NextcloudFile> result = propfind.stream().map(this::davResourceToNextCloudFile).toList();
        return result;
    }

    /**
     * Lists all file in the given folder with the given
     * 
     * @param path  Relative file path. Null for root dir
     * @param depth List depth. -1 for infinite recursion
     * @param tag   System tag to search for
     * @return List of Nextcloud files found
     * @throws IOException
     */
    public List<NextcloudFile> listFilesBySystemTag(@Nullable String path, int depth, @Nonnull SystemTag tag)
            throws IOException {
        if (tag == null) {
            throw new IllegalArgumentException("System tag must not be null");
        }
        final FilterRule fr = new FilterRule(Property.SYSTEM_TAG_ID, Integer.toString(tag.id()));
        final List<NextcloudFile> result = listFiles(path, depth, List.of(fr));
        return result;
    }

    /**
     * List all files in the given path with the given selector applied (only
     * FAVORITE and SYSTEM_TAG_ID are supported as filter rules)
     * 
     * @param path  Relative file path. Null for root dir
     * @param depth List depth. -1 for infinite recursion
     * @param rules List of rules to apply
     * @return List of Nextcloud files found
     * @throws IOException
     */
    protected List<NextcloudFile> listFiles(@Nullable String path, int depth, List<FilterRule> rules)
            throws IOException {
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
            final List<NextcloudFile> result = fqr.getFiles().stream()
                    .map(f -> f.toNextCloudFile(sardine, this.getCurrentUser())).toList();
            return result;
        } else {
            return Collections.emptyList();
        }

    }

    /**
     * List alle files in the given path with the given selector applied (only
     * FAVORITE and SYSTEM_TAG_ID are supported as filter rules)
     * 
     * @param path     Relative file path. Null for root dir
     * @param selector Properties to select and additional filter conditions to
     *                 apply
     * @param depth    List depth. -1 for infinite recursion
     * @return FileQueryResult with the result
     * @throws IOException
     */
    protected FileQueryResult listFiles(@Nullable String path, int depth, @Nonnull FileSelector selector)
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
            logger.debugf("Search queries must start with '/files/$username/: %s --> Prefix is added", query.getFrom());

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
            final Multistatus status = (Multistatus) MULTISTATUS_JAXB_CONTEXT.createUnmarshaller().unmarshal(saxSource);

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
    public void uploadFile(String path, @Nullable String contentType, @Nonnull InputStream content,
            @Nullable String etag,
            @Nullable String lockToken)
            throws IOException {
        final String url = getWebDavFilePath(path);
        final Map<String, String> headers = new HashMap<>();
        headers.putAll(this.buildEtagAndLockHeader(url, etag, lockToken));
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
     * @param path Relative path of the file
     * @throws IOException
     */
    public void deleteFile(String path)
            throws IOException {
        deleteFile(path, (String) null, (String) null);
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
        headers.putAll(buildEtagAndLockHeader(url, etag, lockToken));
        sardine.delete(url, headers);
    }

    /**
     * Builds the necessary If-Match or If headers from etag / locktoken. See
     * rfc4918
     * 
     * @param etag      Etag header, can be null
     * @param lockToken Lock tocken, can be null
     * @return Map containing the headers build. Might be empty but never null
     */
    protected Map<String, String> buildEtagAndLockHeader(String url, @Nullable String etag,
            @Nullable String lockToken) {
        final Map<String, String> headers = new HashMap<>();
        final boolean withEtag = etag != null && !etag.isBlank();
        final boolean withLock = lockEnabled() && lockToken != null && !lockToken.isBlank();
        final String path = url; // url.substring(authProvider.getServer().length());
        if (withEtag && !withLock) {
            headers.put("If-Match", etag);
        } else if (withEtag && withLock) {
            headers.put("If", String.format("<%s> (<%s> [%s])", path, lockToken, etag));
        } else if (!withEtag && withLock) {
            // Should be unlikely case
            headers.put("If", String.format("<%s> (<%s>)", path, lockToken));
        }
        return headers;
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
     * Builder for a MOVE or COPY operation of a file
     * FileMoveOperationBuilder
     */
    public class FileMoveOperationBuilder {
        private enum Operation {
            COPY, MOVE;
        }

        private FileMoveOperationBuilder(Operation op) {
            this.op = op;
        }

        private final Operation op;
        private String sourcePath = null;
        private String targetPath = null;
        private boolean overwrite = false;
        private String sourceEtag = null;
        private String targetEtag = null;
        private String sourceLockToken = null;
        private String targetLockToken = null;

        /**
         * Sets the source path of the operation
         * 
         * @param path Path to set
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder from(String path) {
            this.sourcePath = path;
            return this;
        }

        /**
         * Sets the source file of the operation. Also sets the etag of the file!
         * 
         * @param file File to set
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder from(NextcloudFile file) {
            this.sourcePath = file.path();
            this.sourceEtag = file.etag();
            return this;
        }

        /**
         * Sets a locked file as the source of the operation. Also sets the etag and
         * lock token of the file!
         * 
         * @param file File to set
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder from(NextCloudFileLock file) throws IOException {
            this.sourcePath = file.url();
            this.sourceEtag = file.etag();
            this.sourceLockToken = file.token();
            return this;
        }

        /**
         * Sets the lock for the source file
         * 
         * @param etag      Etag of the source
         * @param lockToken Lock token of the source file
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder withSourceLock(String etag, String lockToken) {
            this.sourceEtag = etag;
            this.sourceLockToken = lockToken;
            return this;
        }

        /**
         * Sets the etag for the source file
         * 
         * @param etag Etag of the source
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder withSourceEtag(String etag) {
            return withSourceLock(etag, null);
        }

        /**
         * Sets the target path of the operation
         * 
         * @param path Path to set
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder to(String path) {
            this.targetPath = path;
            return this;
        }

        /**
         * Sets the target file of the operation. Also sets the etag of the file and
         * enables overwrite!
         * 
         * @param file File to set
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder to(NextcloudFile file) {
            this.targetPath = file.path();
            this.targetEtag = file.etag();
            this.overwrite = true;
            return this;
        }

        /**
         * Sets a locked file as the target of the operation. Also sets the etag and
         * lock token of the file and enables overwrite!
         * 
         * @param file File to set
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder to(NextCloudFileLock file) throws IOException {
            this.targetPath = file.url();
            this.targetEtag = file.etag();
            this.targetLockToken = file.token();
            this.overwrite = true;
            return this;
        }

        /**
         * Sets the lock for the target file. Enables overwrite!
         * 
         * @param etag      Etag of the target
         * @param lockToken Lock token of the target file
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder withTargetLock(String etag, String lockToken) {
            this.targetEtag = etag;
            this.targetLockToken = lockToken;
            this.overwrite = true;
            return this;
        }

        /**
         * Sets the etag for the target file. Enables overwrite!
         * 
         * @param etag Etag of the target
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder withTargetEtag(String etag) {
            return withTargetLock(etag, null);
        }

        /**
         * Whether to overwrite the target if it exists
         * 
         * @param overwrite {@code true} to overwrite if the destination exists,
         *                  {@code false} otherwise.
         * @return This builder for method chaining
         */
        public FileMoveOperationBuilder withOverwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        /**
         * Executes the configured MOVE or COPY operation
         * 
         * @throws IOException
         */
        public void execute() throws IOException {
            if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException("Source path must not be null or empty");
            }
            if (targetPath == null || targetPath.isBlank()) {
                throw new IllegalArgumentException("Target path must not be null or empty");
            }

            final String src = getWebDavFilePath(sourcePath);
            final String target = getWebDavFilePath(targetPath);
            final Map<String, String> headers = new HashMap<>();

            List<String> conditions = new ArrayList<>();
            if (sourceEtag != null && !sourceEtag.isBlank()) {
                if (lockEnabled() && sourceLockToken != null && !sourceLockToken.isBlank()) {
                    conditions.add(String.format("<%s> (<%s> [%s])", src, sourceLockToken, sourceEtag));
                } else {
                    conditions.add(String.format("<%s> ([%s])", src, sourceEtag));
                }
            }
            if (targetEtag != null && !targetEtag.isBlank()) {
                if (lockEnabled() && targetLockToken != null && !targetLockToken.isBlank()) {
                    conditions.add(String.format("<%s> (<%s> [%s])", src, targetLockToken, targetEtag));
                } else {
                    conditions.add(String.format("<%s> ([%s])", src, targetEtag));
                }
            }
            if (!conditions.isEmpty()) {
                headers.put("If", conditions.stream().collect(Collectors.joining(" ")));
            }

            if (op == Operation.COPY) {
                sardine.copy(src, target, overwrite, headers);
            } else {
                sardine.move(src, target, overwrite, headers);
            }
        }
    }

    /**
     * Prepares moving a file from one destination to the other. File id stays the
     * same!
     * 
     */
    public FileMoveOperationBuilder moveFile() {
        return new FileMoveOperationBuilder(
                io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService.FileMoveOperationBuilder.Operation.MOVE);
    }

    /**
     * Prepares coping a file from one destination to the other.
     * 
     */
    public FileMoveOperationBuilder copyFile() {
        return new FileMoveOperationBuilder(
                io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService.FileMoveOperationBuilder.Operation.COPY);
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
     * Checks if file locks are currently enabled
     * 
     * @return
     */
    public boolean lockEnabled() {
        return this.lockEnabled;
    }

    /**
     * Throws an exception if {@link #lockEnabled()} is false
     * 
     * @throws IOException
     */
    protected void assertLockEnabled() throws IOException {
        if (!lockEnabled()) {
            throw new IOException("Property 'nextcloud.file-lock-enabled' not enabled. Files lock not supported");
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
        assertLockEnabled();
        final String url = getWebDavFilePath(path);
        final String token = sardine.lock(url);
        return new NextCloudFileLock(url, token);
    }

    /**
     * Locks the given file remote on the Nextcloud server. Requires the
     * 'files_lock' app to be active
     * 
     * @param file File to lock
     * @return {@link NextCloudFileLock}
     * @throws RuntimeException
     */
    public NextCloudFileLock lockFile(NextcloudFile file) throws IOException {
        assertLockEnabled();
        final String url = file.path();
        return lockFile(url);
    }

}
