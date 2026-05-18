package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable request payload for the AppAPI notifications endpoint.
 *
 * <p>Use {@link io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model.Notification}
 * as a fluent builder to construct instances rather than building this record directly.
 *
 * @see <a href="https://cloud-py-api.github.io/app_api/tech_details/api/notifications.html">Notifications API</a>
 * @see <a href="https://github.com/nextcloud/server/issues/1706">Nextcloud rich-text format spec</a>
 */
public record NotificationRequest(NotificationRequest.Params params) {
    /**
     * Top-level notification parameters required by the AppAPI.
     */
    public static record Params(String object, String object_id, String subject_type,
            Params.SubjectParams subject_params) {
        /**
         * Rich-text subject and message content, each with an optional map of
         * named inline parameters and an optional deep link.
         */
        public static record SubjectParams(String rich_subject,
                Map<String, SubjectParams.Parameter<?>> rich_subject_params,
                String rich_message,
                Map<String, SubjectParams.Parameter<?>> rich_message_params, String link) {

            /**
             * A named inline parameter that can be embedded in a rich-text template
             * using the {@code {name}} placeholder syntax.
             *
             * @param <T> type of the parameter's {@link #id()} value
             */
            public interface Parameter<T> {
                /** Nextcloud parameter type identifier (e.g. {@code "user"}, {@code "file"}). */
                String type();

                /** Unique identifier for this parameter value (e.g. user ID, file ID). */
                T id();

                /** Human-readable display name shown when the template is rendered. */
                String name();

                /**
                 * Creates a user parameter.
                 *
                 * @param id          Nextcloud user ID
                 * @param displayName display name of the user
                 * @return a new {@link User} parameter
                 */
                public static User user(String id, String displayName) {
                    return new User("user", id, displayName);
                }

                /**
                 * Creates a group parameter.
                 *
                 * @param id          Nextcloud group ID
                 * @param displayName display name of the group
                 * @return a new {@link Group} parameter
                 */
                public static Group group(String id, String displayName) {
                    return new Group("group", id, displayName);
                }

                /**
                 * Creates a file parameter.
                 *
                 * @param id          numeric Nextcloud file ID
                 * @param displayName display name of the file
                 * @param path        server-relative path to the file
                 * @param link        URL to the file in the Nextcloud UI
                 * @return a new {@link File} parameter
                 */
                public static File file(int id, String displayName,
                        String path,
                        String link) {
                    return new File("file", id, displayName, path, link);
                }
            }

            /** Rich-text parameter representing a Nextcloud file. */
            public static record File(String type, Integer id, String name, String path, String link)
                    implements SubjectParams.Parameter<Integer> {
            }

            /** Rich-text parameter representing a Nextcloud user. */
            public static record User(String type, String id, String name)
                    implements SubjectParams.Parameter<String> {
            }

            /** Rich-text parameter representing a Nextcloud group. */
            public static record Group(String type, String id, String name)
                    implements SubjectParams.Parameter<String> {
            }

            private static final Pattern MATCH_RICH_MESSAGE = Pattern.compile("\\{([\\w]*)\\}");

            /**
             * Resolves a Nextcloud rich-text template by replacing {@code {name}}
             * placeholders with the {@link Parameter#name()} of the corresponding
             * parameter entry. Placeholders with no matching entry are left as-is.
             *
             * @param text   the rich-text template string
             * @param params map of placeholder name to {@link Parameter}
             * @return the resolved plain-text string
             * @see <a href="https://github.com/nextcloud/server/issues/1706">Rich-text format spec</a>
             */
            public String parseRichText(final String text,
                    final Map<String, SubjectParams.Parameter<?>> params) {
                if (params == null || params.isEmpty()) {
                    return text;
                }

                final Matcher matcher = MATCH_RICH_MESSAGE.matcher(text);
                final StringBuilder sb = new StringBuilder();
                while (matcher.find()) {
                    final String param = matcher.group(1);
                    final SubjectParams.Parameter<?> parameter = params.get(param);
                    if (parameter != null) {
                        matcher.appendReplacement(sb, parameter.name());
                    } else {
                        matcher.appendReplacement(sb, param);
                    }
                }
                matcher.appendTail(sb);
                return sb.toString();
            }

            public String toString() {
                return parseRichText(rich_subject(), rich_subject_params()) + "\n"
                        + parseRichText(rich_message(), rich_message_params());
            }
        }
    }
}