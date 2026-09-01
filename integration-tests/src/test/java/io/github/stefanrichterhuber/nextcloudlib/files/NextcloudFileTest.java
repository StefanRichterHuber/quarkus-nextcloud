package io.github.stefanrichterhuber.nextcloudlib.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService.NextCloudFileLock;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FileQueryResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Condition;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Property;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Query;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudFileTest {

    private static final String ROOT_DIR = "/TESTDIR";

    private final static String TEST_TEXT1 = """
            # Ode to the Cloud

            Up in the servers, quiet and vast,
            Where files are stored and memories last,
            A markdown file in a folder sleeps,
            While Nextcloud faithfully its promise keeps.

            Through tunnels of light the data flows,
            Past patches and diffs, the revision grows,
            Each change a whisper, each save a breath,
            A document lives its little life past death.

            So here's to the cloud, both humble and bright,
            That keeps our small poems through day and through night.

                        """;

    private final static String TEST_TEXT2 = """
            Ode to the Cloud

            O Cloud, you silver shelf above my desk,
            where markdown files drift soft as morning mist —
            no hard drive spins, no cable knots, no risk
            of coffee spilled on all that I have kissed

            with careful keystrokes into being. You
            hold every revision, every draft,
            each clumsy edit timestamped, kept true,
            the whole embarrassing creative craft.

            You speak in WebDAV, answer ETags,
            demand preconditions be precisely met —
            a bureaucrat in vapour, filing flags
            on every write I haven't patched quite yet.

            And still I love you, Cloud, with all your rules:
            you remember everything I am too human to.
                        """;

    @Inject
    NextcloudFileService service;

    @Test
    public void searchFilesTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        Query query = Query.select(Property.DISPLAY_NAME, Property.GET_CONTENT_TYPE, Property.GET_ETAG)
                .from(ROOT_DIR)
                .where(Condition.isFile());

        FileQueryResult result = service.search(query);

        assertNotNull(result);
        assertNotNull(result.getFiles());
    }

    @Test
    public void basicFileAccessTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        List<NextcloudFile> files = service.listFiles(ROOT_DIR, -1);

        assertNotNull(files);
    }

    @Test
    public void moveFileTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        // Introduce space to test path
        String rawFileName = UUID.randomUUID().toString() + " " + "-test.md";
        String targetRawFileName = UUID.randomUUID().toString() + " " + "-test-trgt.md";
        String filename = ROOT_DIR + "/" + rawFileName;
        String targetFileName = ROOT_DIR + "/" + targetRawFileName;
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        NextcloudFile f1 = service.getFile(filename);
        assertNotNull(f1);

        service.moveFile().from(filename).to(targetFileName).execute();

        try {
            f1 = service.getFile(filename);
            fail();
        } catch (IOException e) {
            // Expected - source file no longer exists
        }

        NextcloudFile f2 = service.getFile(targetFileName);
        assertNotNull(f2);
    }

    @Test
    public void moveFileWithLockTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        // Introduce space to test path
        String rawFileName = UUID.randomUUID().toString() + " " + "-test.md";
        String targetRawFileName = UUID.randomUUID().toString() + " " + "-test-trgt.md";
        String filename = ROOT_DIR + "/" + rawFileName;
        String targetFileName = ROOT_DIR + "/" + targetRawFileName;
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        NextcloudFile f1 = service.getFile(filename);
        assertNotNull(f1);

        NextCloudFileLock lck = service.lockFile(f1);

        // File should not be movable with out the lock token
        try {
            service.moveFile().from(filename).to(targetFileName).execute();
            fail();
        } catch (IOException e) {
            // Expected
        }

        // File should be movable with lock
        service.moveFile().from(lck).to(targetFileName).execute();

        NextcloudFile f2 = service.getFile(targetFileName);
        assertNotNull(f2);

        try {
            f1 = service.getFile(filename);
            fail();
        } catch (IOException e) {
            // Source file no long exists
        }
    }

    @Test
    public void copyFileTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        // Introduce space to test path
        String rawFileName = UUID.randomUUID().toString() + " " + "-test.md";
        String targetRawFileName = UUID.randomUUID().toString() + " " + "-test-trgt.md";
        String filename = ROOT_DIR + "/" + rawFileName;
        String targetFileName = ROOT_DIR + "/" + targetRawFileName;
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        NextcloudFile f1 = service.getFile(filename);
        assertNotNull(f1);

        service.copyFile().from(filename).to(targetFileName).execute();

        // Both files, source and target no exists
        f1 = service.getFile(filename);
        assertNotNull(f1);

        NextcloudFile f2 = service.getFile(targetFileName);
        assertNotNull(f2);
    }

    @Test
    public void overwriteFileWithEtagTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        // Introduce space to test path
        String rawFileName = UUID.randomUUID().toString() + " " + "-test.md";
        String filename = ROOT_DIR + "/" + rawFileName;
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));
        try {
            NextcloudFile rev1 = service.getFile(filename);
            assertNotNull(rev1);
            assertEquals(rawFileName, rev1.dataSource().getName());

            rev1 = service.downloadFileImmediately(rev1);
            assertNotNull(rev1);
            assertEquals(rawFileName, rev1.dataSource().getName());
            String etag = rev1.etag();

            try (InputStream is = rev1.dataSource().getInputStream()) {
                String c = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(TEST_TEXT1, c);
            }

            try {
                service.uploadFile(filename, "text/markdown",
                        new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)),
                        "\"rndValueInsteadofEtag\"", (String) null);
                fail();
            } catch (IOException e) {
                // Expected
            }

            service.uploadFile(filename, "text/markdown",
                    new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)), etag, (String) null);

            NextcloudFile rev2 = service.getFile(filename);
            assertNotNull(rev2);

            try (InputStream is = rev2.dataSource().getInputStream()) {
                String c = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(TEST_TEXT2, c);
            }
        } finally {
            service.deleteFile(filename, null, (String) null);
        }
    }

    @Test
    public void deleteFileWithEtagTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";

        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        NextcloudFile rev1 = service.getFile(filename);
        assertNotNull(rev1);
        String etag = rev1.etag();
        assertNotNull(etag);

        try {
            service.deleteFile(filename, "\"rndValueInsteadofEtag\"", (String) null);
            fail("Should not work");
        } catch (IOException e) {
            // Expected
        }

        service.deleteFile(filename, etag, (String) null);

    }

    @Test
    public void fileLockTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";

        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        NextCloudFileLock lck = service.lockFile(filename);

        // Should fail without lock token
        try {
            service.uploadFile(filename, "text/markdown",
                    new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)));
            fail();
        } catch (IOException e) {
            // Expected
        }

        NextcloudFile file = lck.file();
        String etag = file.etag();
        String lockTocken = lck.token();

        // Upload of content or delete should fail with wrong etag or locktocken
        try {
            service.uploadFile(filename, "text/markdown",
                    new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)), "\"rndValueInsteadofEtag\"",
                    lockTocken);
            fail();
        } catch (IOException e) {
            // Expected
        }
        try {
            service.uploadFile(filename, "text/markdown",
                    new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)), etag,
                    "\"rndValueInsteadofLockToken\"");
            fail();
        } catch (IOException e) {
            // Expected
        }
        try {
            service.deleteFile(filename, etag,
                    "\"rndValueInsteadofLockToken\"");
            fail();
        } catch (IOException e) {
            // Expected
        }
        try {
            service.deleteFile(filename, "\"rndValueInsteadofEtag\"",
                    lockTocken);
            fail();
        } catch (IOException e) {
            // Expected
        }

        // Works with correct etag and lock token
        lck.uploadContent("text/markdown",
                new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)));

        // File can also be deleted with the correct tokens
        lck.deleteFile();

        // Finally unlock the file (no-op if the file was previously deleted)
        lck.unlock();
    }

    @Test
    public void fileRevisionTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        // Introduce space to test path
        String rawFileName = UUID.randomUUID().toString() + " " + "-test.md";
        String filename = ROOT_DIR + "/" + rawFileName;
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));
        try {
            NextcloudFile rev1 = service.getFile(filename);
            assertNotNull(rev1);
            assertEquals(rawFileName, rev1.dataSource().getName());

            try (InputStream is = rev1.dataSource().getInputStream()) {
                String c = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(TEST_TEXT1, c);
            }

            service.uploadFile(filename, "text/markdown",
                    new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)));

            NextcloudFile rev2 = service.getFile(filename);
            assertNotNull(rev2);

            try (InputStream is = rev2.dataSource().getInputStream()) {
                String c = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(TEST_TEXT2, c);
            }

            List<NextcloudFile> revs = service.listFileRevisions(rev2);
            assertNotNull(revs);
            assertFalse(revs.isEmpty());
            assertEquals(2, revs.size());

        } finally {
            service.deleteFile(filename, null, (String) null);
        }

    }

    @Test
    public void queryFilesTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";

        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        FileQueryResult result = service
                .search(Query.select(Property.DISPLAY_NAME).from(ROOT_DIR).where(Condition.isFile()));
        assertNotNull(result);
    }

    @Test
    public void getFileByIDTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";

        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        try {
            NextcloudFile rev1 = service.getFile(filename);
            assertNotNull(rev1);

            NextcloudFile rev2 = service.getFileById(rev1.fileId());

            assertEquals(rev1, rev2);
        } finally {
            service.deleteFile(filename, null, (String) null);
        }
    }

}
