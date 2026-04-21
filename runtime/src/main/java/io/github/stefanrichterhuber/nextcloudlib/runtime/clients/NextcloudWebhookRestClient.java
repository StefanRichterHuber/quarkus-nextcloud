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
 * @see <a href=
 *      "https://docs.nextcloud.com/server/latest/admin_manual/webhook_listeners/index.html">https://docs.nextcloud.com/server/latest/admin_manual/webhook_listeners/index.html</a>
 */
@RegisterClientHeaders(NextcloudAPIAdminClientHeaders.class)
public interface NextcloudWebhookRestClient {
    public static enum AuthMethod {
        @JsonProperty("header")
        HEADER,
        @JsonProperty("none")
        NONE,
    }

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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static record WebhookMessage(
            String id,
            String appId,
            String userId,
            @Nonnull HTTPMethod httpMethod,
            @Nonnull String uri,
            @Nonnull String event,
            JsonNode eventFilter,
            String userIdFilter,
            Map<String, String> headers,
            AuthMethod authMethod,
            JsonNode authData,
            TokenNeeded tokenNeeded) {

        public record TokenNeeded(List<String> user_ids, List<String> user_roles) {
        }

        /**
         * Factory method to create a WebhookMessage for registry requests. This is used
         * to create a WebhookMessage with the necessary fields for registry requests,
         * while ignoring the fields that are not needed for registry requests (like id,
         * appId, userId).
         * 
         * @param method
         * @param uri
         * @param event
         * @param headers
         * @param authMethod
         * @param authData
         * @param tokenNeeded
         * @return
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
    }

    /**
     * Lists all registered webhooks
     * <br>
     * <b>Requires Admin privileges</b>
     * 
     * @return List of registered webhooks
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks")
    OCSMessage<List<WebhookMessage>> listRegisteredWebhooks();

    /**
     * Gets a registered webhook by its id
     * <br>
     * <b>Requires Admin privileges</b>
     * 
     * @param webhookId the id of the webhook to get
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<WebhookMessage> getWebhook(@PathParam("webhookId") String webhookId);

    /**
     * Registers a new webhook with the given configuration
     * <br>
     * <b>Requires Admin privileges</b>
     * 
     * @param webhook the configuration of the webhook to register
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks")
    OCSMessage<WebhookMessage> registerWebhook(WebhookMessage webhook);

    /**
     * Updates a new webhook with the given configuration
     * <br>
     * <b>Requires Admin privileges</b>
     * 
     * @param webhookId the id of the webhook to update
     * @param webhook   the updated webhook configuration
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<WebhookMessage> updateWebhook(@PathParam("webhookId") String webhookId, WebhookMessage webhook);

    /**
     * Deletes a new webhook with the given configuration
     * <br>
     * <b>Requires Admin privileges</b>
     * 
     * @param webhookId the id of the webhook to delete
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<Boolean> deleteWebhook(@PathParam("webhookId") String webhookId);
}
