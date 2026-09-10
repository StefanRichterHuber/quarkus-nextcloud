package io.github.stefanrichterhuber.nextcloudlib.runtime.models.search;

public interface Renderable {

    StringBuilder render(StringBuilder sb, int indent);

    default String indent(int indent) {
        return " ".repeat(indent * 4);
    }

    /**
     * Escapes text so it can be safely embedded as character data or an attribute
     * value into the rendered WebDAV XML. Prevents malformed requests and query
     * manipulation when caller-supplied values contain XML metacharacters.
     *
     * @param s raw text, may be {@code null}
     * @return the XML-escaped text, or an empty string if {@code s} is {@code null}
     */
    default String escapeXmlText(String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&apos;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
