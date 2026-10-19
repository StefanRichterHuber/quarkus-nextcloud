package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class WebDavPathsTest {

    @Test
    public void testEncode() {
        assertEquals("Documents/my%20file.md", WebDavPaths.encode("Documents/my file.md"));
        assertEquals("a%2Bb/%23hash%20%25%3F.md", WebDavPaths.encode("a+b/#hash %?.md"));
        assertEquals("%C3%A4rger/", WebDavPaths.encode("ärger/"));
        assertNull(WebDavPaths.encode(null));
    }

    @Test
    public void testDecode() {
        assertEquals("Documents/my file.md", WebDavPaths.decode("Documents/my%20file.md"));
        // '+' is a literal plus in paths, not a space
        assertEquals("a+b/#hash %?.md", WebDavPaths.decode("a+b/%23hash%20%25%3F.md"));
        assertEquals("ärger/", WebDavPaths.decode("%C3%A4rger/"));
        assertNull(WebDavPaths.decode(null));
    }

    @Test
    public void testRoundTrip() {
        final String path = "Ordner mit Leerzeichen/ä ö ü + & # % ;=,.md";
        assertEquals(path, WebDavPaths.decode(WebDavPaths.encode(path)));
    }
}
