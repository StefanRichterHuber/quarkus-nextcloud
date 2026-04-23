package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIAdminClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST client for managing Nextcloud webhook listeners via the OCS API.
 * <p>
 * All operations require admin privileges. The client is configured with
 * {@link NextcloudAPIAdminClientHeaders} to inject the required authentication
 * headers automatically.
 *
 * @see <a href=
 *      "https://docs.nextcloud.com/server/latest/admin_manual/webhook_listeners/index.html">Nextcloud
 *      Webhook Listeners documentation</a>
 */
@RegisterClientHeaders(NextcloudAPIAdminClientHeaders.class)
public interface NextcloudWebhookRestClient {

    /**
     * Authentication method used when Nextcloud calls the webhook endpoint.
     */
    public static enum AuthMethod {
        /** Pass authentication credentials as an HTTP header. */
        @JsonProperty("header")
        HEADER,
        /** No authentication is used. */
        @JsonProperty("none")
        NONE,
    }

    /**
     * HTTP method used when Nextcloud calls the webhook endpoint.
     */
    public static enum HTTPMethod {
        @JsonProperty("GET")
        GET,
        @JsonProperty("POST")
        POST,
        @JsonProperty("PUT")
        PUT,
        @JsonProperty("DELETE")
        DELETE,
        @JsonProperty("PATCH")
        PATCH,
    }

    /**
     * Represents a Nextcloud webhook registration.
     * <p>
     * When creating or updating a webhook, prefer using
     * {@link WebhookMessage.Builder}
     * via {@link #builder()} to construct the message. The fields {@code id},
     * {@code appId}, and {@code userId} are assigned by Nextcloud and should be
     * {@code null} in registration requests.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static record WebhookMessage(
            /** Nextcloud-assigned webhook ID; {@code null} for registration requests. */
            String id,
            /**
             * Nextcloud app that registered the webhook; {@code null} for registration
             * requests.
             */
            String appId,
            /**
             * Nextcloud user that registered the webhook; {@code null} for registration
             * requests.
             */
            String userId,
            /** HTTP method Nextcloud uses to call the webhook endpoint. */
            @Nonnull HTTPMethod httpMethod,
            /** Target URL Nextcloud calls when the event fires. */
            @Nonnull String uri,
            /** Fully-qualified Nextcloud event class name that triggers this webhook. */
            @Nonnull String event,
            /**
             * Optional JMESPath filter applied to the event payload; {@code null} means no
             * filter.
             */
            JsonNode eventFilter,
            /**
             * Restrict delivery to a specific Nextcloud user ID; {@code null} means all
             * users.
             */
            String userIdFilter,
            /** Additional HTTP headers sent with each webhook call. */
            Map<String, String> headers,
            /** Authentication method used when calling the webhook endpoint. */
            AuthMethod authMethod,
            /** Authentication credentials matching the chosen {@link AuthMethod}. */
            JsonNode authData,
            /**
             * Controls which Nextcloud users or roles receive a sign token in the payload.
             */
            TokenNeeded tokenNeeded) {

        /**
         * Specifies which Nextcloud users or roles should receive a sign token
         * embedded in the webhook payload.
         *
         * @param user_ids   list of Nextcloud user IDs that receive a token
         * @param user_roles list of Nextcloud role names that receive a token
         */
        public record TokenNeeded(List<String> user_ids, List<String> user_roles) {
        }

        /**
         * Returns a new {@link Builder} pre-populated with this message's values,
         * useful for creating modified copies.
         *
         * @return a new builder initialised from this record
         */
        public Builder toBuilder() {
            return new Builder()
                    .userId(userId)
                    .httpMethod(httpMethod)
                    .uri(uri)
                    .event(event)
                    .eventFilter(eventFilter)
                    .userIdFilter(userIdFilter)
                    .headers(headers)
                    .authMethod(authMethod)
                    .authData(authData)
                    .tokenNeeded(tokenNeeded);
        }

        /**
         * Returns a new {@link Builder} for constructing a {@link WebhookMessage}.
         *
         * @return a fresh builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Factory method to create a {@link WebhookMessage} for registration requests,
         * omitting the server-assigned fields ({@code id}, {@code appId},
         * {@code userId}).
         *
         * @param method      HTTP method Nextcloud uses to call the webhook endpoint
         * @param uri         target URL of the webhook endpoint
         * @param event       fully-qualified Nextcloud event class name
         * @param headers     additional HTTP headers sent with each call
         * @param authMethod  authentication method for the webhook call
         * @param authData    authentication credentials matching the chosen method
         * @param tokenNeeded users/roles that receive a sign token in the payload
         * @return a new {@link WebhookMessage} ready to be sent to the registration API
         */
        public static WebhookMessage createRegistryRequest(HTTPMethod method, String uri, String event,
                Map<String, String> headers,
                AuthMethod authMethod, Map<String, String> authData, TokenNeeded tokenNeeded) {
            final ObjectMapper mapper = new ObjectMapper();
            return new WebhookMessage(
                    null,
                    null,
                    null,
                    method,
                    uri,
                    event,
                    null,
                    null,
                    headers,
                    authMethod,
                    mapper.valueToTree(authData),
                    tokenNeeded);
        }

        /**
         * Builder for {@link WebhookMessage}.
         * <p>
         * The fields {@code httpMethod}, {@code uri}, and {@code event} are required;
         * all other fields are optional and default to {@code null}.
         *
         * <pre>{@code
         * WebhookMessage msg = WebhookMessage.builder()
         *         .httpMethod(HTTPMethod.POST)
         *         .uri("https://example.com/hook")
         *         .event("OCA\\Webhooks\\Events\\WebhookEvent")
         *         .authMethod(AuthMethod.HEADER)
         *         .authData(Map.of("Authorization", "Bearer token"))
         *         .build();
         * }</pre>
         */
        public static final class Builder {

            private String id;
            private String appId;
            private String userId;
            private HTTPMethod httpMethod;
            private String uri;
            private String event;
            private JsonNode eventFilter;
            private String userIdFilter;
            private Map<String, String> headers;
            private AuthMethod authMethod;
            private JsonNode authData;
            private WebhookMessage.TokenNeeded tokenNeeded;

            private Builder() {
            }

            /**
             * @param userId Nextcloud user ID that owns the webhook; leave {@code null} for
             *               new registrations
             */
            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            /**
             * @param httpMethod HTTP method Nextcloud uses to call the endpoint (required)
             */
            public Builder httpMethod(@Nonnull HTTPMethod httpMethod) {
                this.httpMethod = httpMethod;
                return this;
            }

            /**
             * @param uri target URL Nextcloud calls when the event fires (required)
             */
            public Builder uri(@Nonnull String uri) {
                this.uri = uri;
                return this;
            }

            /**
             * @param event fully-qualified Nextcloud event class name (required)
             */
            public Builder event(@Nonnull String event) {
                this.event = event;
                return this;
            }

            /**
             * @param eventFilter optional JMESPath expression to filter event payloads;
             *                    {@code null} means no filter
             */
            public Builder eventFilter(JsonNode eventFilter) {
                this.eventFilter = eventFilter;
                return this;
            }

            /**
             * @param userIdFilter restrict delivery to this Nextcloud user ID;
             *                     {@code null} delivers to all users
             */
            public Builder userIdFilter(String userIdFilter) {
                this.userIdFilter = userIdFilter;
                return this;
            }

            /**
             * @param headers additional HTTP headers sent with each webhook call
             */
            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }

            /**
             * @param authMethod authentication method used when calling the endpoint
             */
            public Builder authMethod(AuthMethod authMethod) {
                this.authMethod = authMethod;
                return this;
            }

            /**
             * @param authData authentication credentials as a raw JSON node
             */
            public Builder authData(JsonNode authData) {
                this.authData = authData;
                return this;
            }

            /**
             * Sets authentication credentials from a plain string map, converting it to a
             * JSON node internally.
             *
             * @param authData authentication key-value pairs
             */
            public Builder authData(Map<String, String> authData) {
                this.authData = new ObjectMapper().valueToTree(authData);
                return this;
            }

            /**
             * @param tokenNeeded users/roles that receive a sign token in the payload
             */
            public Builder tokenNeeded(WebhookMessage.TokenNeeded tokenNeeded) {
                this.tokenNeeded = tokenNeeded;
                return this;
            }

            /**
             * Builds the {@link WebhookMessage}.
             *
             * @return the constructed message
             * @throws IllegalStateException if {@code httpMethod}, {@code uri}, or
             *                               {@code event} are {@code null}
             */
            public WebhookMessage build() {
                if (httpMethod == null) {
                    throw new IllegalStateException("httpMethod is required");
                }
                if (uri == null) {
                    throw new IllegalStateException("uri is required");
                }
                if (event == null) {
                    throw new IllegalStateException("event is required");
                }
                return new WebhookMessage(id, appId, userId, httpMethod, uri, event,
                        eventFilter, userIdFilter, headers, authMethod, authData, tokenNeeded);
            }
        }
    }

    /**
     * Lists all registered webhooks.
     * <p>
     * <b>Requires admin privileges.</b>
     *
     * @return list of all registered webhooks
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks")
    OCSMessage<List<WebhookMessage>> listRegisteredWebhooks();

    /**
     * Returns a single registered webhook by its ID.
     * <p>
     * <b>Requires admin privileges.</b>
     *
     * @param webhookId the Nextcloud-assigned webhook ID
     * @return the matching webhook, wrapped in an OCS envelope
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<WebhookMessage> getWebhook(@PathParam("webhookId") String webhookId);

    /**
     * Registers a new webhook.
     * <p>
     * <b>Requires admin privileges.</b>
     *
     * @param webhook the webhook configuration; server-assigned fields ({@code id},
     *                {@code appId}, {@code userId}) must be {@code null}
     * @return the registered webhook with its assigned ID, wrapped in an OCS
     *         envelope
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks")
    OCSMessage<WebhookMessage> registerWebhook(WebhookMessage webhook);

    /**
     * Updates an existing webhook's configuration.
     * <p>
     * <b>Requires admin privileges.</b>
     *
     * @param webhookId the Nextcloud-assigned ID of the webhook to update
     * @param webhook   the updated webhook configuration
     * @return the updated webhook, wrapped in an OCS envelope
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<WebhookMessage> updateWebhook(@PathParam("webhookId") String webhookId, WebhookMessage webhook);

    /**
     * Deletes a registered webhook.
     * <p>
     * <b>Requires admin privileges.</b>
     *
     * @param webhookId the Nextcloud-assigned ID of the webhook to delete
     * @return {@code true} if the webhook was deleted, wrapped in an OCS envelope
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<Boolean> deleteWebhook(@PathParam("webhookId") String webhookId);
}
