package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

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
 * <p>On {@link #registerWebhooks()} it queries the current list of registered
 * webhooks, skips entries that already point to this application's callback URL
 * (unless {@link NextcloudWebhookBuildConfig#alwaysRegister()} is {@code true}),
 * and registers any missing ones. The IDs of newly registered webhooks are
 * kept in memory so they can be cleaned up by {@link #deleteRegisteredWebhooks()}.
 */
@ApplicationScoped
public class NextcloudWebhookRegistrationService {
    @Inject
    Logger logger;

    @Inject
    NextcloudWebhookBuildConfig staticConfig;

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

    private final List<WebhookMessage> registeredWebhooks = new ArrayList<>();

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

            String host = staticConfig.host();
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

    private WebhookMessage buildMessage(String className) {
        return WebhookMessage.createRegistryRequest(
                HTTPMethod.POST,
                webhookUrl(),
                className,
                Map.of(
                        "Content-Type", MediaType.APPLICATION_JSON,
                        "Accept", MediaType.APPLICATION_JSON),
                AuthMethod.HEADER,
                Map.of(staticConfig.header(), secretHolder.getSecret()),
                new TokenNeeded(List.of(), List.of("trigger")));
    }

    /**
     * Registers one new webhook for the given Nextcloud PHP event class name.
     *
     * @param client    REST client configured for the Nextcloud instance
     * @param className fully-qualified Nextcloud PHP event class name to listen for
     * @return the registered {@link WebhookMessage} returned by Nextcloud, or
     *         {@code null} when registration fails
     */
    private WebhookMessage registerOne(NextcloudWebhookRestClient client, String className) {
        try {
            final WebhookMessage request = buildMessage(className);
            final OCSMessage<WebhookMessage> response = client.registerWebhook(request);
            if (response.ocs().meta().statuscode() == 200) {
                logger.infof("Registered webhook for event '%s' and webhook %s to '%s'", className,
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
     * {@link NextcloudEventInvoker} instances. Already-registered webhooks at the
     * same callback URL are skipped unless
     * {@link NextcloudWebhookBuildConfig#alwaysRegister()} is {@code true}.
     * Errors are caught and logged so that registration failures do not prevent
     * the application from starting.
     */
    public void registerWebhooks() {

        Set<String> eventClassNames = new HashSet<>();
        for (NextcloudEventInvoker invoker : invokers) {
            eventClassNames.addAll(Set.of(invoker.events()));
        }

        if (eventClassNames.isEmpty()) {
            return;
        }

        String url = webhookUrl();
        NextcloudWebhookRestClient client = buildClient();

        try {
            OCSMessage<List<WebhookMessage>> listResponse = client.listRegisteredWebhooks();
            if (listResponse.ocs().meta().statuscode() != 200) {
                logger.errorf("Failed to list registered webhooks: %s",
                        listResponse.ocs().meta().message());
                return;
            }

            List<WebhookMessage> registered = listResponse.ocs().data();
            if (registered == null) {
                registered = List.of();
            }

            for (String className : eventClassNames) {
                WebhookMessage existing = registered.stream()
                        .filter(w -> url.equals(w.uri()) && className.equals(w.event()))
                        .findFirst()
                        .orElse(null);

                if (existing != null) {
                    if (staticConfig.alwaysRegister()) {
                        logger.infof("Re-registering webhook for %s at %s (alwaysRegister=true)",
                                className, url);
                        client.deleteWebhook(existing.id());
                        existing = null;
                    } else {
                        logger.infof("Webhook for %s already registered at %s", className, url);
                    }
                }

                if (existing == null) {
                    logger.debugf("Registering webhook for %s at %s", className, url);
                    this.registeredWebhooks.add(registerOne(client, className));
                }
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
     * Deletes all webhooks that were registered during this application's lifetime,
     * provided {@link NextcloudWebhookBuildConfig#deregisterWebhooksOnShutdown()} is
     * {@code true}. Errors for individual deletions are caught and logged.
     */
    public void deleteRegisteredWebhooks() {

        if (staticConfig.deregisterWebhooksOnShutdown() && !this.registeredWebhooks.isEmpty()) {
            final NextcloudWebhookRestClient client = buildClient();
            logger.debugf("Deleteing registered webhooks: %s",
                    this.registeredWebhooks.stream().map(m -> m.id()).collect(Collectors.joining(", ")));

            for (WebhookMessage wm : this.registeredWebhooks) {
                try {
                    client.deleteWebhook(wm.id());
                    logger.infof("Successfully deleted webhook with id %s", wm.id());
                } catch (Exception e) {
                    logger.errorf(e, "Failed to delete webhook with id %s", wm.id());
                }
            }
        }
    }

}
