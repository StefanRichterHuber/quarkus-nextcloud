package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Registers Nextcloud webhook listeners for every event class name discovered
 * at build time via {@link OnNextcloudEvent}.
 *
 * <p>
 * This bean is only added to the CDI container when at least one
 * {@link OnNextcloudEvent} handler method is present.
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
public class NextcloudWebhookStartupRegistrar {

    @Inject
    Logger logger;

    @Inject
    NextcloudWebhookRegistrationService service;

    /**
     * Called on application shutdown. Delegates to
     * {@link NextcloudWebhookRegistrationService#deleteRegisteredWebhooks()} to
     * remove any webhooks registered by this application from Nextcloud.
     *
     * @param ev the Quarkus shutdown event
     */
    void onStop(@Observes ShutdownEvent ev) {
        service.deleteRegisteredWebhooks();
    }

    /**
     * On startup of this application register all event webhooks requested
     */
    @Startup
    void registerWebhooks() {
        service.registerWebhooks();
    }
}
