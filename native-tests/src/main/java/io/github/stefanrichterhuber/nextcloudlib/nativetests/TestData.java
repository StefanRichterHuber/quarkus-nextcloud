package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Shared test content of the scenarios
 */
public final class TestData {
    public static final String ROOT_DIR = "/NATIVE_TESTDIR";

    public static final String TEXT1 = """
            # Ode to the Cloud

            Up in the servers, quiet and vast,
            Where files are stored and memories last,
            A markdown file in a folder sleeps,
            While Nextcloud faithfully its promise keeps.
            """;

    public static final String TEXT2 = """
            Ode to the Cloud

            O Cloud, you silver shelf above my desk,
            where markdown files drift soft as morning mist —
            no hard drive spins, no cable knots, no risk
            of coffee spilled on all that I have kissed
            """;

    private TestData() {
    }

    public static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return a new, unique file name in {@link #ROOT_DIR}. Contains characters
     *         that need to be encoded in WebDAV URLs.
     */
    public static String newFileName() {
        return ROOT_DIR + "/" + UUID.randomUUID() + " native ä+#.md";
    }
}
