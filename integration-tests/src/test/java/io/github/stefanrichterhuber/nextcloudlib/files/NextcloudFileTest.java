package io.github.stefanrichterhuber.nextcloudlib.files;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FileQueryResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextCloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Condition;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Property;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Query;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
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
        List<NextCloudFile> files = service.listFiles(ROOT_DIR, -1);

        assertNotNull(files);
    }

    @Test
    public void overwriteFileWithEtagTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));
        try {
            NextCloudFile rev1 = service.getFile(filename);
            assertNotNull(rev1);
            String etag = rev1.etag();

            service.uploadFile(filename, "text/markdown",
                    new ByteArrayInputStream(TEST_TEXT2.getBytes(StandardCharsets.UTF_8)), etag, (String) null);

            NextCloudFile rev2 = service.getFile(filename);
            assertNotNull(rev2);
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

        NextCloudFile rev1 = service.getFile(filename);
        assertNotNull(rev1);
        String etag = rev1.etag();
        assertNotNull(etag);

        service.deleteFile(filename, etag, (String) null);

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

}
