package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.AuthMethod;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.HTTPMethod;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.WebhookMessage;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.WebhookMessage.TokenNeeded;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

/**
 * Service responsible for registering and deregistering Nextcloud webhook
 * listeners against the Nextcloud webhook API.
 *
 * <p>
 * On {@link #registerWebhooks()} it queries the current list of registered
 * webhooks, skips entries that already point to this application's callback URL
 * (unless {@link NextcloudWebhookConfig#alwaysRegister()} is
 * {@code true}),
 * and registers any missing ones. The IDs of newly registered webhooks are
 * kept in memory so they can be cleaned up by
 * {@link #deleteRegisteredWebhooks()}.
 */
@ApplicationScoped
public class NextcloudWebhookRegistrationService {
    @Inject
    Logger logger;

    @Inject
    NextcloudWebhookBuildConfig staticConfig;

    @Inject
    NextcloudWebhookConfig config;

    @Inject
    NextcloudWebhookSecretHolder secretHolder;

    @Inject
    @NextcloudAdmin
    NextcloudAuthProvider adminAuth;

    @Inject
    Instance<NextcloudEventInvoker> invokers;

    @Inject
    ManagedExecutor scheduledExecutorService;

    @Inject
    NextcloudExappConfig exappConfig;

    @Inject
    NextcloudExappAppConfig appConfig;

    @Inject
    ObjectMapper objectMapper;

    private record InvokerAndWebhookMessages(NextcloudEventInvoker invoker, List<WebhookMessage> webhooks) {
    }

    private final Map<String, InvokerAndWebhookMessages> invokersById = new ConcurrentHashMap<>();

    private String webhookUrl() {
        if (exappConfig.enabled()) {
            // This is an nextcloud exapp -> fetch the host from a different location
            final String host = String.format("%s://%s:%d", appConfig.protocol(), appConfig.host(),
                    appConfig.port());

            String path = staticConfig.path();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return host + path;
        } else {

            String host = config.host();
            if (host.endsWith("/")) {
                host = host.substring(0, host.length() - 1);
            }
            String path = staticConfig.path();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return host + path;
        }
    }

    private NextcloudWebhookRestClient buildClient() {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(adminAuth.getServer()))
                .followRedirects(true)
                .build(NextcloudWebhookRestClient.class);
    }

    private WebhookMessage buildMessage(String id, String className, boolean tokenNeeded, JsonNode eventFilter) {
        final List<String> tokenNeedList = tokenNeeded ? List.of("trigger") : List.of();
        final String url = webhookUrl() + "/" + id;

        return WebhookMessage.createRegistryRequest(
                HTTPMethod.POST,
                url,
                className,
                eventFilter,
                Map.of(
                        "Content-Type", MediaType.APPLICATION_JSON,
                        "Accept", MediaType.APPLICATION_JSON),
                AuthMethod.HEADER,
                Map.of(config.header(), secretHolder.getSecret()),
                new TokenNeeded(List.of(), tokenNeedList));
    }

    /**
     * Registers one new webhook for the given Nextcloud PHP event class name.
     *
     * @param client      REST client configured for the Nextcloud instance
     * @param id          Unique id of the webhook handler (== hash of its class
     *                    name)
     * @param className   fully-qualified Nextcloud PHP event class name to listen
     *                    for
     * @param tokenNeeded Request a auth token
     * @param eventFilter Optional event filter
     * @return the registered {@link WebhookMessage} returned by Nextcloud, or
     *         {@code null} when registration fails
     */
    private WebhookMessage registerWebhook(NextcloudWebhookRestClient client, String id, String className,
            boolean tokenNeeded,
            JsonNode eventFilter) {
        try {
            final WebhookMessage request = buildMessage(id, className, tokenNeeded, eventFilter);
            final OCSMessage<WebhookMessage> response = client.registerWebhook(request);
            if (response.ocs().meta().statuscode() == 200) {
                logger.infof("Registered webhook with id %s for event '%s' and method %s to '%s'",
                        response.ocs().data().id(), className,
                        request.httpMethod(),
                        request.uri());
                return response.ocs().data();
            } else {
                logger.errorf("Failed to register webhook for %s: %s",
                        className, response.ocs().meta().message());

            }
        } catch (WebApplicationException e) {
            logger.errorf(e, "Failed to register webhook for %s: HTTP %d",
                    className, e.getResponse().getStatus());
        }
        return null;
    }

    /**
     * Registers webhooks for all event class names declared by the known
     * {@link NextcloudEventInvoker} instances.
     */
    public void registerWebhooks() {
        final NextcloudWebhookRestClient client = buildClient();
        final List<WebhookMessage> registered = fetchExistingWebhooks(client);
        final String webhookBaseUrl = webhookUrl();
        try {

            for (NextcloudEventInvoker invoker : invokers) {
                final String invokerId = DigestUtils.sha256Hex(invoker.getClass().getName());
                final String url = webhookBaseUrl + "/" + invokerId;
                final List<String> events = List.of(invoker.events());
                final String filter = invoker.filter();
                final JsonNode eventFilterNode = filter != null && !filter.isEmpty()
                        ? objectMapper.readValue(filter, JsonNode.class)
                        : null;

                final List<WebhookMessage> webhooks = new ArrayList<>();
                // Find all existing webhooks matching this event handler
                List<WebhookMessage> matchingWebhooks = registered.stream()
                        .filter(w -> w.uri().equals(url))
                        .filter(w -> events.contains(w.event()))
                        .filter(w -> Objects.equals(w.eventFilter(), eventFilterNode))
                        .toList();

                if (!matchingWebhooks.isEmpty()) {
                    if (config.alwaysRegister()) {
                        for (WebhookMessage existing : matchingWebhooks) {
                            logger.infof("Re-registering webhook for %s at %s (nextcloud.webhook.alwaysRegister=true)",
                                    existing.event(), url);
                            client.deleteWebhook(existing.id());
                        }
                        matchingWebhooks = List.of();
                    } else {
                        for (WebhookMessage existing : matchingWebhooks) {
                            logger.infof(
                                    "Webhook for %s already registered at %s (nextcloud.webhook.alwaysRegister=false)",
                                    existing.event(), url);
                            webhooks.add(existing);
                        }
                    }
                }
                for (String event : events) {
                    if (matchingWebhooks.stream().noneMatch(w -> Objects.equals(w.event(), event))) {
                        final WebhookMessage result = this.registerWebhook(client, invokerId, event,
                                invoker.requestAuthToken(),
                                eventFilterNode);
                        if (result != null) {
                            webhooks.add(result);
                            logger.infof("Successfully registered webhook with id %s for event '%s'", result.id(),
                                    event);
                        } else {
                            logger.errorf("Failed to register webhook with for event '%s'", event);

                        }
                    }
                }
                final InvokerAndWebhookMessages handler = new InvokerAndWebhookMessages(invoker, webhooks);
                this.invokersById.put(invokerId, handler);
            }
        } catch (WebApplicationException e) {
            logger.errorf(e,
                    "Could not reach Nextcloud webhook API (HTTP %d); skipping webhook registration.",
                    e.getResponse().getStatus());
        } catch (Exception e) {
            logger.errorf(e, "Unexpected error during webhook registration; skipping.");
        }
    }

    /**
     * Fetches the existing webhooks for this url
     * 
     * @param client RestClient for webhook registration
     * @return
     */
    private List<WebhookMessage> fetchExistingWebhooks(NextcloudWebhookRestClient client) {
        final OCSMessage<List<WebhookMessage>> listResponse = client.listRegisteredWebhooks();
        if (listResponse.ocs().meta().statuscode() != 200) {
            logger.errorf("Failed to list registered webhooks: %s",
                    listResponse.ocs().meta().message());
            return List.of();
        }
        final List<WebhookMessage> registered = listResponse.ocs().data();
        if (registered == null) {
            return List.of();
        }
        final String url = webhookUrl();
        return registered.stream().filter(w -> w.uri().startsWith(url)).toList();
    }

    /**
     * Deletes all webhooks that were registered during this application's lifetime,
     * provided {@link NextcloudWebhookConfig#deregisterWebhooksOnShutdown()}
     * is
     * {@code true}. Errors for individual deletions are caught and logged.
     */
    public void deleteRegisteredWebhooks() {
        if (config.deregisterWebhooksOnShutdown() && !this.invokersById.isEmpty()) {
            final NextcloudWebhookRestClient client = buildClient();

            for (InvokerAndWebhookMessages invokersAndWebhookMessages : this.invokersById.values()) {
                for (WebhookMessage wm : invokersAndWebhookMessages.webhooks()) {
                    try {
                        client.deleteWebhook(wm.id());
                        logger.infof("Successfully deleted webhook with id %s",
                                wm.id());
                    } catch (Exception e) {
                        logger.errorf(e, "Failed to delete webhook with id %s",
                                wm.id());
                    }
                }
            }
            this.invokersById.clear();
        }
    }

    /**
     * Returns the stored invoker by its id
     * 
     * @param id ID of the Invoker (generated during registration of webhooks)
     * @return Invoker found
     */
    public NextcloudEventInvoker getEventHandlerById(String id) {
        return Optional.ofNullable(invokersById.get(id)).map(i -> i.invoker()).orElse(null);
    }

}
