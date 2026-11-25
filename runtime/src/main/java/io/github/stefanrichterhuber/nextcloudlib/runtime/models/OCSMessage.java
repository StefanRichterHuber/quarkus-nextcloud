package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import io.quarkus.runtime.annotations.RegisterForReflection;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@RegisterForReflection
public record OCSMessage<T>(OCSMessage.OCS<T> ocs) {
    /**
     * Checks if the response is successful based on the status code in the meta
     * information.
     * A status code of 200 or 100 is considered successful.
     *
     * @return
     */
    public boolean isOk() {
        return ocs != null && ocs.isOk();
    }

    /**
     * @param meta Meta information (status) of the response
     * @param data Payload of the response. {@code null} if Nextcloud returned an
     *             empty array for a non-collection payload, which it does for
     *             failed requests (an empty PHP array is serialized as
     *             {@code []}).
     */
    @RegisterForReflection
    public static record OCS<T>(OCS.Meta meta, @JsonDeserialize(using = EmptyArrayAsNullDeserializer.class) T data) {

        /**
         * Checks if the response is successful based on the status code in the meta
         * information.
         * A status code of 200 or 100 is considered successful.
         *
         * @return
         */
        public boolean isOk() {
            return meta != null && meta.isOk();
        }

        @RegisterForReflection
        public static record Meta(String status, int statuscode, String message, String totalitems,
                String itemsperpage) {

            /**
             * Checks if the response is successful based on the status code in the meta
             * information.
             * A status code of 200 or 100 is considered successful.
             *
             * @return
             */
            public boolean isOk() {
                return statuscode == 200 || statuscode == 100;
            }
        }
    }

    /**
     * Deserializer for {@link OCS#data()}. Nextcloud returns {@code "data": []}
     * on failed requests (e.g. {@code statuscode} 996), regardless of the type of
     * the payload. For payloads that cannot be represented by a JSON array (beans,
     * scalars) an empty array is therefore mapped to {@code null}, so the caller
     * can inspect {@link OCSMessage#isOk()} instead of getting a deserialization
     * error. Collections, arrays, {@link Object} and {@link JsonNode} payloads are
     * deserialized unchanged.
     */
    @RegisterForReflection
    public static final class EmptyArrayAsNullDeserializer extends ValueDeserializer<Object> {
        private final JavaType type;
        private final ValueDeserializer<Object> delegate;

        /**
         * Used by Jackson before contextualization
         */
        public EmptyArrayAsNullDeserializer() {
            this(null, null);
        }

        private EmptyArrayAsNullDeserializer(JavaType type, ValueDeserializer<Object> delegate) {
            this.type = type;
            this.delegate = delegate;
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            final JavaType contextualType = property != null ? property.getType() : ctxt.getContextualType();
            final ValueDeserializer<Object> deserializer = ctxt.findContextualValueDeserializer(contextualType,
                    property);
            if (contextualType.isContainerType() || contextualType.isArrayType()
                    || contextualType.isJavaLangObject() || contextualType.isTypeOrSubTypeOf(JsonNode.class)) {
                // An empty array is a valid value for these types
                return deserializer;
            }
            return new EmptyArrayAsNullDeserializer(contextualType, deserializer);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            if (p.currentToken() == JsonToken.START_ARRAY) {
                final JsonNode node = ctxt.readTree(p);
                // Non-empty arrays are passed on (e.g. for UNWRAP_SINGLE_VALUE_ARRAYS)
                return node.isEmpty() ? null : ctxt.readTreeAsValue(node, type);
            }
            return delegate.deserialize(p, ctxt);
        }

        @Override
        public Object getNullValue(DeserializationContext ctxt) {
            return delegate != null ? delegate.getNullValue(ctxt) : null;
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return delegate != null ? delegate.getAbsentValue(ctxt) : null;
        }
    }
}
