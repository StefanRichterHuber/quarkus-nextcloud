package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.events;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookRegistrationService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppDisabledEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppEnabledEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * ExApp-mode counterpart of
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookStartupRegistrar}.
 * Instead of registering webhooks on application startup, this bean registers
 * them when the ExApp is enabled by Nextcloud and removes them when it is
 * disabled. This ensures the webhook registrations stay in sync with the ExApp
 * lifecycle managed by AppAPI.
 */
@ApplicationScoped
public class NextcloudWebhookEnabledRegistrar {
    @Inject
    Logger logger;

    @Inject
    NextcloudWebhookRegistrationService service;

    /**
     * Called when Nextcloud disables this ExApp. Delegates to
     * {@link NextcloudWebhookRegistrationService#deleteRegisteredWebhooks()} to
     * remove all webhooks registered by this application.
     *
     * @param ev the CDI disabled event fired by {@link io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppEnabledHandler}
     */
    void onStop(@Observes ExAppDisabledEvent ev) {
        service.deleteRegisteredWebhooks();
    }

    /**
     * Called when Nextcloud enables this ExApp. Delegates to
     * {@link NextcloudWebhookRegistrationService#registerWebhooks()} to register
     * all event webhooks discovered at build time.
     *
     * @param ev the CDI enabled event fired by {@link io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppEnabledHandler}
     */
    void registerWebhooks(@Observes ExAppEnabledEvent ev) {
        service.registerWebhooks();
    }
}
