package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Nextcloud webhook integration.
 *
 * <p>
 * Uses {@link ConfigPhase#BUILD_AND_RUN_TIME_FIXED} so that the Vert.x route
 * can be registered at build time (using {@code path()}) while all runtime
 * collaborators ({@link NextcloudWebhookRegistrar},
 * {@link NextcloudWebhookSecretHolder}, {@link NextcloudWebhookHandler}) can
 * inject the same interface via CDI.
 * </p>
 *
 * <h2>Properties</h2>
 * <ul>
 * <li>{@code nextcloud.webhook.path} – path at which the webhook endpoint is
 * mounted (default {@code /webhook}). Changing this requires a rebuild.</li>
 * <li>{@code nextcloud.webhook.host} – publicly reachable base URL of this
 * application as seen by Nextcloud, e.g. {@code https://myapp.example.com}.
 * Combined with {@code path} to form the full callback URL.</li>
 * <li>{@code nextcloud.webhook.secret} – shared secret token sent in the
 * authentication header. When absent a cryptographically random secret is
 * generated at startup and a warning is logged.</li>
 * <li>{@code nextcloud.webhook.header} – HTTP header used to transmit the
 * shared secret (default {@code X-Nextcloud-Webhook-Secret}).</li>
 * <li>{@code nextcloud.webhook.always-register} – when {@code true} any
 * existing webhook registration is deleted and re-created on every startup
 * (default {@code false}).</li>
 * </ul>
 */
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "nextcloud.webhook")
public interface NextcloudWebhookBuildConfig {

    /** Path at which the Nextcloud webhook endpoint is mounted. */
    @WithDefault("/webhook")
    String path();

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
     * When {@code true} the webhook registred are deleted on shutdown
     * 
     */
    @WithDefault("true")
    boolean deregisterWebhooksOnShutdown();
}
