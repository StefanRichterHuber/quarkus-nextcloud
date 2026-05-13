package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.AuthMethod;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.HTTPMethod;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.WebhookMessage;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient.WebhookMessage.TokenNeeded;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

/**
 * Registers Nextcloud webhook listeners for every event class name discovered
 * at build time via {@link OnNextcloudEvent}.
 *
 * <p>
 * This bean is only added to the CDI container when at least one
 * {@link OnNextcloudEvent} handler method is present. On startup it queries the
 * Nextcloud webhook API, skips already-registered webhooks (unless
 * {@link NextcloudWebhookBuildConfig#alwaysRegister()} is {@code true}), and
 * registers any missing ones.
 * </p>
 *
 * <p>
 * The full callback URL is constructed as
 * {@code nextcloud.webhook.host + nextcloud.webhook.path}, e.g.
 * {@code https://myapp.example.com/webhook}.
 * </p>
 *
 * <p>
 * Failures (Nextcloud unreachable, auth errors, etc.) are caught and logged so
 * that a registration problem never prevents the application from starting.
 * </p>
 */
@ApplicationScoped
public class NextcloudWebhookRegistrar {

    private static final Logger LOG = Logger.getLogger(NextcloudWebhookRegistrar.class);

    @Inject
    NextcloudWebhookBuildConfig config;

    @Inject
    NextcloudWebhookSecretHolder secretHolder;

    @Inject
    @NextcloudAdmin
    NextcloudAuthProvider adminAuth;

    @Inject
    Instance<NextcloudEventInvoker> invokers;

    private String webhookUrl() {
        String host = config.host();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        String path = config.path();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return host + path;
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
                Map.of(config.header(), secretHolder.getSecret()),
                new TokenNeeded(List.of(), List.of("trigger")));
    }

    private void registerOne(NextcloudWebhookRestClient client, String className) {
        try {
            OCSMessage<WebhookMessage> response = client.registerWebhook(buildMessage(className));
            if (response.ocs().meta().statuscode() == 200) {
                LOG.infof("Registered webhook for event: %s", className);
            } else {
                LOG.errorf("Failed to register webhook for %s: %s",
                        className, response.ocs().meta().message());
            }
        } catch (WebApplicationException e) {
            LOG.errorf(e, "Failed to register webhook for %s: HTTP %d",
                    className, e.getResponse().getStatus());
        }
    }

    /**
     * On start-up collects all NextcloudEventInvokers and select all events to
     * handle
     */
    @Startup
    void registerWebhooks() {

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
                LOG.errorf("Failed to list registered webhooks: %s",
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
                    if (config.alwaysRegister()) {
                        LOG.infof("Re-registering webhook for %s at %s (alwaysRegister=true)",
                                className, url);
                        client.deleteWebhook(existing.id());
                        existing = null;
                    } else {
                        LOG.infof("Webhook for %s already registered at %s", className, url);
                    }
                }

                if (existing == null) {
                    LOG.infof("Registering webhook for %s at %s", className, url);
                    registerOne(client, className);
                }
            }
        } catch (WebApplicationException e) {
            LOG.errorf(e,
                    "Could not reach Nextcloud webhook API (HTTP %d); skipping webhook registration.",
                    e.getResponse().getStatus());
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during webhook registration; skipping.");
        }
    }
}
