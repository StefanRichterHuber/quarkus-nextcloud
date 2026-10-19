package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.ROOT_DIR;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.TEXT1;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.TEXT2;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.newFileName;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.stream;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService.NextCloudFileLock;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FileQueryResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Condition;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Property;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Query;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * WebDAV scenarios (Sardine, JAXB, Apache HttpClient) - the highest native
 * image risk of the extension.
 */
@Path("/native/files")
public class FileScenarios {
    @Inject
    NextcloudFileService service;

    /**
     * Upload, PROPFIND (list, get) and download of a file
     */
    @POST
    @Path("/upload-list-download")
    public void uploadListDownload() throws Exception {
        service.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        service.uploadFile(filename, "text/markdown", stream(TEXT1));
        try {
            final List<NextcloudFile> files = service.listFiles(ROOT_DIR, -1);
            Check.isTrue(files.stream().anyMatch(f -> f.path().endsWith(filename.substring(1))),
                    "Uploaded file not listed");

            final NextcloudFile file = service.downloadFileImmediately(service.getFile(filename));
            Check.equal(TEXT1, file.readToString(StandardCharsets.UTF_8), "Downloaded content");
            Check.notNull(file.etag(), "ETag missing");
            Check.notNull(file.fileId(), "File id missing");
        } finally {
            service.deleteFile(filename);
        }
    }

    @POST
    @Path("/move-copy")
    public void moveCopy() throws Exception {
        service.createDirectories(ROOT_DIR);
        final String source = newFileName();
        final String moved = newFileName();
        final String copied = newFileName();
        service.uploadFile(source, "text/markdown", stream(TEXT1));

        service.moveFile().from(source).to(moved).execute();
        Check.failsWithIOException(() -> service.getFile(source), "Source of move still exists");
        Check.notNull(service.getFile(moved), "Move target missing");

        service.copyFile().from(moved).to(copied).execute();
        Check.notNull(service.getFile(moved), "Copy source missing");
        Check.notNull(service.getFile(copied), "Copy target missing");

        service.deleteFile(moved);
        service.deleteFile(copied);
    }

    /**
     * Conditional requests (If-Match) on upload and delete
     */
    @POST
    @Path("/etag")
    public void etag() throws Exception {
        service.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        service.uploadFile(filename, "text/markdown", stream(TEXT1));

        final String etag = service.getFile(filename).etag();
        Check.failsWithIOException(() -> service.uploadFile(filename, "text/markdown", stream(TEXT2),
                "\"rndValueInsteadofEtag\"", (String) null), "Upload with wrong ETag succeeded");
        service.uploadFile(filename, "text/markdown", stream(TEXT2), etag, (String) null);
        Check.equal(TEXT2, service.getFile(filename).readToString(StandardCharsets.UTF_8), "Overwritten content");

        final String etag2 = service.getFile(filename).etag();
        Check.failsWithIOException(() -> service.deleteFile(filename, "\"rndValueInsteadofEtag\"", (String) null),
                "Delete with wrong ETag succeeded");
        service.deleteFile(filename, etag2, (String) null);
    }

    /**
     * WebDAV LOCK / UNLOCK (Sardine lock models)
     */
    @POST
    @Path("/lock")
    public void lock() throws Exception {
        service.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        service.uploadFile(filename, "text/markdown", stream(TEXT1));

        final NextCloudFileLock lck = service.lockFile(filename);
        Check.notNull(lck.token(), "Lock token missing");
        Check.failsWithIOException(() -> service.uploadFile(filename, "text/markdown", stream(TEXT2)),
                "Upload without lock token succeeded");

        lck.uploadContent("text/markdown", stream(TEXT2));
        lck.unlock();
        service.deleteFile(filename);
    }

    /**
     * File versions (custom PROPFIND on the versions endpoint)
     */
    @POST
    @Path("/revisions")
    public void revisions() throws Exception {
        service.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        service.uploadFile(filename, "text/markdown", stream(TEXT1));
        try {
            final NextcloudFile rev1 = service.getFile(filename);
            // Nextcloud only keeps a version if the next upload is in a different second
            Thread.sleep(1100);
            service.uploadFile(filename, "text/markdown", stream(TEXT2));

            final List<NextcloudFile> revs = service.listFileRevisions(service.getFile(filename));
            Check.equal(2, revs.size(), "Number of revisions");
            Check.equal(TEXT1, revs.get(0).readToString(StandardCharsets.UTF_8), "Content of first revision");
            Check.notNull(service.getFileRevision(filename, revs.get(0).etag()), "Revision by ETag");
            Check.notNull(service.getFileByModifyDate(filename, rev1.modified()), "Revision by modification date");
        } finally {
            service.deleteFile(filename);
        }
    }

    /**
     * WebDAV SEARCH (own JAXB context, SAX parser, DOM properties) and lookups by
     * file id
     */
    @POST
    @Path("/search")
    public void search() throws Exception {
        service.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        service.uploadFile(filename, "text/markdown", stream(TEXT1));
        try {
            final FileQueryResult result = service.search(
                    Query.select(Property.DISPLAY_NAME, Property.GET_CONTENT_TYPE, Property.GET_ETAG)
                            .from(ROOT_DIR)
                            .where(Condition.isFile()));
            Check.notNull(result, "Search result");
            Check.isFalse(result.getFiles().isEmpty(), "Search found no files");

            final NextcloudFile file = service.getFile(filename);
            Check.equal(file, service.getFileById(file.fileId()), "File by id");
            Check.isTrue(service.getFilesByFileIds(List.of(file.fileId())).stream()
                    .anyMatch(f -> f.path().endsWith(filename.substring(1))), "Files by ids");
        } finally {
            service.deleteFile(filename);
        }
    }
}
