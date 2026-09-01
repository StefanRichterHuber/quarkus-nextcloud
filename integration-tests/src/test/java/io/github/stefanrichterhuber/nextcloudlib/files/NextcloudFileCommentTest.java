package io.github.stefanrichterhuber.nextcloudlib.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCommentService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.Comment;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudFileCommentTest {
    private static final String COMMENT = "This is the first comment";

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
    NextcloudFileService service;

    @Inject
    NextcloudCommentService commentService;

    @Test
    public void addRemoveCommentTest() throws IOException {
        service.createDirectories(ROOT_DIR);
        // Introduce space to test path
        final String rawFileName = UUID.randomUUID().toString() + " " + "-test.md";
        final String filename = ROOT_DIR + "/" + rawFileName;
        service.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));
        try {
            final NextcloudFile rev1 = service.getFile(filename);
            assertNotNull(rev1);
            assertEquals(rawFileName, rev1.dataSource().getName());

            final List<Comment> commentsv1 = commentService.getCommentsOfFile(rev1);
            assertNotNull(commentsv1);
            assertTrue(commentsv1.isEmpty());

            commentService.addCommentToFile(rev1, COMMENT);

            final List<Comment> commentsv2 = commentService.getCommentsOfFile(rev1);
            assertNotNull(commentsv2);
            assertFalse(commentsv2.isEmpty());

            assertTrue(commentsv2.stream().anyMatch(c -> c.message().equals(COMMENT)));

            // Now delete comment to clean up
            commentsv2.stream().forEach(c -> {
                try {
                    commentService.deleteComment(c);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            service.deleteFile(filename, null, (String) null);
        }
    }
}
