package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves and holds the shared secret used to authenticate incoming Nextcloud
 * webhook requests.
 *
 * <p>
 * If {@code nextcloud.webhook.secret} is set in the application configuration
 * the configured value is used as-is. Otherwise a 256-bit (32-byte)
 * cryptographically random secret is generated via {@link SecureRandom} and a
 * warning is logged explaining that the secret will change on every restart,
 * which will break the Nextcloud webhook registration.
 * </p>
 */
@ApplicationScoped
public class NextcloudWebhookSecretHolder {

    private static final Logger LOG = Logger.getLogger(NextcloudWebhookSecretHolder.class);

    @Inject
    NextcloudWebhookBuildConfig config;

    private String secret;

    @PostConstruct
    void init() {
        secret = config.secret().orElseGet(() -> {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String generated = HexFormat.of().formatHex(bytes);
            LOG.warn("nextcloud.webhook.secret is not configured. " +
                    "A random secret has been generated for this startup: " + generated + ". " +
                    "This secret changes on every restart, causing the Nextcloud webhook " +
                    "registration to fail after a restart. " +
                    "Set nextcloud.webhook.secret in your configuration to a stable value.");
            return generated;
        });
    }

    /**
     * Returns the effective shared secret, either configured or randomly generated.
     */
    public String getSecret() {
        return secret;
    }
}
