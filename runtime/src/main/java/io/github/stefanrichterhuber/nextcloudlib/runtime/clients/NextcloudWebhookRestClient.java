package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

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
 * @see https://docs.nextcloud.com/server/latest/admin_manual/webhook_listeners/index.html
 */
@RegisterClientHeaders(NextcloudAPIAdminClientHeaders.class)
public interface NextcloudWebhookRestClient {

    public static record WebhookMessage(
            String id,
            String userId,
            @Nonnull String httpMethod,
            @Nonnull String uri,
            @Nonnull String event,
            Map<String, Object> eventFilter,
            String userIdFilter,
            Map<String, Object> headers,
            String authMethod,
            Map<String, Object> authData,
            TokenNeeded tokenNeeded) {

        public record TokenNeeded(Map<String, String> user_ids, Map<String, String> user_roles) {
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
     * @param webhookId
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
     * @param webhook
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
     * @param webhook
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
     * @param webhook
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v2.php/apps/webhook_listeners/api/v1/webhooks/{webhookId}")
    OCSMessage<Boolean> deleteWebhook(@PathParam("webhookId") String webhookId);
}
