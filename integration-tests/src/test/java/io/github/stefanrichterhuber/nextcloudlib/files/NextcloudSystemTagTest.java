package io.github.stefanrichterhuber.nextcloudlib.files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SystemTag;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class NextcloudSystemTagTest {
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

    @Inject
    NextcloudSystemTagService tagService;

    @Inject
    NextcloudFileService fileService;

    @Test
    public void createSystemTagTest() throws IOException {
        SystemTag st = tagService.addSystemTag("hello", true, true, true);
        assertNotNull(st);

        fileService.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";
        fileService.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        NextcloudFile file = fileService.getFile(filename);

        tagService.addTagToFile(file, st);

        List<SystemTag> fileTags = tagService.listSystemTagsOfFile(file);
        assertTrue(fileTags.contains(st));
    }
}
