package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "nextcloud.webhook")
public interface NextcloudWebhookConfig {

    /** Publicly reachable host URL of this application, without trailing slash. */
    @WithDefault("http://localhost:8080")
    String host();

    /**
     * Shared secret sent by Nextcloud in the authentication header.
     * If absent a random secret is generated at startup.
     */
    Optional<String> secret();

    /** HTTP header name used to transmit the shared secret. */
    @WithDefault("X-Nextcloud-Webhook-Secret")
    String header();

    /**
     * When {@code true} the webhook registration is always re-created on startup.
     */
    @WithDefault("false")
    boolean alwaysRegister();

    /**
     * When {@code true}, webhooks registered by this application are deleted
     * from Nextcloud on shutdown.
     *
     * @return {@code true} to deregister webhooks on shutdown, defaults to
     *         {@code true}
     */
    @WithDefault("true")
    boolean deregisterWebhooksOnShutdown();
}
