package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Converts between the plain file paths exposed by the API (e.g.
 * {@code /Documents/my file.md}) and the percent-encoded paths used in WebDAV
 * URLs (e.g. {@code /Documents/my%20file.md}).
 */
public final class WebDavPaths {

    private WebDavPaths() {
    }

    /**
     * Percent-encodes each segment of the given plain path. Slashes separating
     * the segments are kept.
     *
     * @param path plain path, e.g. {@code Documents/my file.md}
     * @return encoded path, e.g. {@code Documents/my%20file.md}
     */
    public static String encode(String path) {
        if (path == null) {
            return null;
        }
        // limit -1 keeps trailing empty segments, so a trailing slash survives
        return Arrays.stream(path.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    /**
     * Decodes a percent-encoded path as returned by WebDAV (in hrefs). In
     * contrast to form encoding, a {@code +} is kept as is.
     *
     * @param path encoded path, e.g. {@code Documents/my%20file.md}
     * @return plain path, e.g. {@code Documents/my file.md}
     */
    public static String decode(String path) {
        if (path == null) {
            return null;
        }
        return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
