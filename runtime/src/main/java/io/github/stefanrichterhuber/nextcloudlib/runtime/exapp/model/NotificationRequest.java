package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @see https://github.com/nextcloud/server/issues/1706
 * @see https://cloud-py-api.github.io/app_api/tech_details/api/notifications.html
 */
public record NotificationRequest(NotificationRequest.Params params) {
    public static record Params(String object, String object_id, String subject_type,
            Params.SubjectParams subject_params) {
        public static record SubjectParams(String rich_subject,
                Map<String, SubjectParams.Parameter<?>> rich_subject_params,
                String rich_message,
                Map<String, SubjectParams.Parameter<?>> rich_message_params, String link) {

            public interface Parameter<T> {
                String type();

                T id();

                String name();

                public static User user(String id, String displayName) {
                    return new User("user", id, displayName);
                }

                public static Group group(String id, String displayName) {
                    return new Group("group", id, displayName);
                }

                public static File file(int id, String displayName,
                        String path,
                        String link) {
                    return new File("file", id, displayName, path, link);
                }
            }

            public static record File(String type, Integer id, String name, String path, String link)
                    implements SubjectParams.Parameter<Integer> {
            }

            public static record User(String type, String id, String name)
                    implements SubjectParams.Parameter<String> {
            }

            public static record Group(String type, String id, String name)
                    implements SubjectParams.Parameter<String> {
            }

            private static final Pattern MATCH_RICH_MESSAGE = Pattern.compile("\\{([\\w]*)\\}");

            /**
             * Parses the rich text field and generates a full text representation
             * 
             * @param text
             * @param params
             * @return
             * @see https://github.com/nextcloud/server/issues/1706
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