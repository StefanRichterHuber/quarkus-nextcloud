package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Model for 'Declarative Settings'
 * 
 * @see https://cloud-py-api.github.io/app_api/tech_details/api/settings.html
 */
public record DeclarativeSettings(Scheme formScheme) {

    public record Scheme(
            String id,
            int priority,
            String section_type,
            String section_id,
            String title,
            String description,
            List<? extends Field<?>> fields) {

        public enum FieldType {
            MULTI_SELECT("multi-select"),
            TEXT("text"),
            MAIL("email"),
            TEL("tel"),
            URL("url"),
            NUMBER("number"),
            PASSWORD("password"),
            SELECT("select"),
            CHECKBOX("checkbox"),
            MULTI_CHECKBOX("multi-checkbox"),
            RADIO("radio");

            private final String value;

            private FieldType(String value) {
                this.value = value;
            }

            @JsonValue
            public String value() {
                return value;
            }
        }

        public record Option(String name, String value) {
            /**
             * Creates a new option with name and value
             * 
             * @param name  Key of the option
             * @param value Value of the option
             * @return Option
             */
            public static Option of(String name, String value) {
                return new Option(name, value);
            }

            /**
             * Creates a list of options with k1,v1,k2,v2 ...
             * 
             * @param keyValuePairs
             * @return
             */
            public static List<Option> of(String... keyValuePairs) {
                if (keyValuePairs == null || keyValuePairs.length == 0) {
                    return Collections.emptyList();
                }
                if (keyValuePairs.length % 2 != 0) {
                    throw new IllegalArgumentException("keyValuePairs must have even length");
                }
                final List<Option> options = new ArrayList<>(keyValuePairs.length / 2);
                for (int i = 0; i < keyValuePairs.length; i += 2) {
                    options.add(of(keyValuePairs[i], keyValuePairs[i + 1]));
                }
                return options;
            }
        }

        @JsonInclude(Include.NON_NULL)
        public interface Field<TDefault> {
            String id();

            String title();

            String description();

            FieldType type();

            @JsonProperty("default")
            TDefault defaultValue();

            public static Field<String> radio(String id, String title, String description, String label,
                    String placeholder,
                    String defaultValue, List<Option> options) {
                return new RadioField(id, title, description, FieldType.RADIO, placeholder, label, defaultValue,
                        options);
            }

            public static Field<Boolean> checkbox(String id, String title, String description, String label,
                    boolean defaultValue) {
                return new CheckboxField(id, title, description, FieldType.CHECKBOX, label,
                        defaultValue);
            }

            public static Field<Map<String, Boolean>> multiCheckbox(String id, String title, String description,
                    Map<String, Boolean> defaultValue, List<Option> options) {
                return new MultiCheckbox(id, title, description, FieldType.MULTI_CHECKBOX, defaultValue, options);
            }

            public static Field<List<String>> multiSelect(String id, String title, String description,
                    String placeholder, List<String> options, List<String> defaultValue) {
                return new GenericSelectField<List<String>>(id, title, description, FieldType.MULTI_SELECT, options,
                        placeholder,
                        defaultValue);
            }

            public static Field<String> select(String id, String title, String description, String placeholder,
                    List<String> options, String defaultValue) {
                return new GenericSelectField<String>(id, title, description, FieldType.SELECT, options, placeholder,
                        defaultValue);
            }

            public static Field<String> text(String id, String title, String description, String placeholder,
                    String defaultValue) {
                return new GenericTextField<String>(id, title, description, FieldType.TEXT, placeholder, defaultValue);
            }

            public static Field<String> email(String id, String title, String description, String placeholder,
                    String defaultValue) {
                return new GenericTextField<String>(id, title, description, FieldType.MAIL, placeholder, defaultValue);
            }

            public static Field<String> phone(String id, String title, String description, String placeholder,
                    String defaultValue) {
                return new GenericTextField<String>(id, title, description, FieldType.TEL, placeholder, defaultValue);
            }

            public static Field<String> url(String id, String title, String description, String placeholder,
                    String defaultValue) {
                return new GenericTextField<String>(id, title, description, FieldType.URL, placeholder, defaultValue);
            }

            public static Field<Number> number(String id, String title, String description, String placeholder,
                    Number defaultValue) {
                return new GenericTextField<Number>(id, title, description, FieldType.NUMBER, placeholder, defaultValue);
            }

            public static Field<String> password(String id, String title, String description, String placeholder,
                    String defaultValue) {
                return new GenericTextField<String>(id, title, description, FieldType.PASSWORD, placeholder,
                        defaultValue);
            }
        }

        private record GenericSelectField<TDefault>(String id, String title, String description, FieldType type,
                List<String> options, String placeholder, @JsonProperty("default") TDefault defaultValue)
                implements Field<TDefault> {
        }

        private record RadioField(String id, String title, String description, FieldType type, String placeholder,
                String label,
                @JsonProperty("default") String defaultValue, List<Option> options) implements Field<String> {
        }

        private record GenericTextField<T>(String id, String title, String description, FieldType type,
                String placeholder,
                @JsonProperty("default") T defaultValue) implements Field<T> {
        }

        private record CheckboxField(String id, String title, String description, FieldType type, String label,
                @JsonProperty("default") Boolean defaultValue) implements Field<Boolean> {
        }

        private record MultiCheckbox(String id, String title, String description, FieldType type,
                @JsonProperty("default") Map<String, Boolean> defaultValue, List<Option> options)
                implements Field<Map<String, Boolean>> {
        }
    }
}
