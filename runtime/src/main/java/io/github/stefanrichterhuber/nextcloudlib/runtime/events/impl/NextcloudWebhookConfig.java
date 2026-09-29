package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.time.Duration;
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

    /**
     * 
     * Whether to clean up the ephemeral auth tokens which can be requested (see
     * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent#requestAuthToken()})
     * This functionality is a temporary work-around for nextcloud issue that the
     * ephermal auth tokens are not properly cleaned up by the Nextcloud server.
     * 
     * @return
     * @see <a href="https://github.com/nextcloud/server/issues/64682">Github-Issue
     *      64682</a>
     */
    @WithDefault("false")
    boolean cleanupAuthTokens();

    /**
     * When {@link #cleanupAuthTokens()} is {@code true}, set a delay before the
     * auth tokens is deleted after the actual handler method is called. This is
     * usefull if there is some further work on other threads expected even if the
     * actual handler method finishes. Defaults to {@code 0s}
     * 
     * @return
     */
    @WithDefault("PT0S")
    Duration cleanupAuthTokenDelay();

    /**
     * In test enviroments (with nextcloud dev services) the credentials delivered
     * by the nextcloud server contains
     * the wrong server url. If this value is set, the server url in the token is
     * replaced by this value
     * 
     * @return
     */
    Optional<String> fixedWebhookTokenUrl();
}
