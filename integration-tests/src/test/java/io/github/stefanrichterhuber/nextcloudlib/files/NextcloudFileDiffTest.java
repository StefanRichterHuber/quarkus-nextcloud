package io.github.stefanrichterhuber.nextcloudlib.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileDiffService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class NextcloudFileDiffTest {
    private static final String ROOT_DIR = "/TESTDIRDIFF";
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
    private final static String PATCH1 = """
            -- a/poem.txt
            +++ b/poem.txt
            @@ -1 +1 @@
            -# Ode to the Cloud
            +# Hello to the cloud
            """;
    @Inject
    NextcloudFileService service;

    @Inject
    NextcloudFileDiffService diffService;

    @Test
    public void patchFileWithEtagTest() throws IOException, PatchFailedException {
        service.createDirectories(ROOT_DIR);
        String filename = ROOT_DIR + "/" + UUID.randomUUID().toString() + ".md";

        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        try {

            NextcloudFile rev1 = service.getFile(filename);
            assertNotNull(rev1);
            String rev1Text = rev1.readToString(StandardCharsets.UTF_8);
            assertEquals(TEST_TEXT1, rev1Text);

            final List<String> patchContent = Arrays.asList(PATCH1.split("\n"));
            Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchContent);

            diffService.applyContentPatch(rev1, patch, 4);

            NextcloudFile rev2 = service.getFile(filename);
            assertNotNull(rev2);

            String rev2Text = rev2.readToString(StandardCharsets.UTF_8);
            assertTrue(rev2Text.startsWith("# Hello to the cloud"));

        } finally {
            service.deleteFile(filename, null, (String) null);
        }
    }
}
