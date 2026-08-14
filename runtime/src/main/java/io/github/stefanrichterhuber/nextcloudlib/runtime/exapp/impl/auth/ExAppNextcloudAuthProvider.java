package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * ExApp-specific {@link NextcloudAuthProvider} that resolves credentials from
 * the AppAPI runtime configuration ({@code nextcloud.url},
 * {@code nextcloud.user},
 * and the shared {@code app.secret}).
 *
 * <p>
 * Registered as a {@link DefaultBean} with a priority 1000 above the standard
 * config-based provider, so it wins the CDI resolution when ExApp mode is
 * active.
 * User and server properties are mutable so that per-request credentials
 * injected
 * by
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppAuthHandler}
 * can override the defaults.
 */
@DefaultBean
@RequestScoped
@Priority(ExAppNextcloudAuthProvider.PRIORITY)
public class ExAppNextcloudAuthProvider implements NextcloudAuthProvider {
    /** CDI priority used by this provider — higher than the standard provider. */
    public static final int PRIORITY = NextcloudAuthProvider.STANDARD_PRIORITY + 1000;

    private NextcloudUserCredentials creds = null;

    @Inject
    NextcloudExappAppConfig config;

    @Inject
    @ConfigProperty(name = "nextcloud.url")
    Optional<String> serverUrl;

    @Inject
    @ConfigProperty(name = "nextcloud.user")
    Optional<String> user;

    @Override
    public void setCredentials(NextcloudUserCredentials creds) {
        this.creds = creds;
    }

    @Override
    public NextcloudUserCredentials getCredentials() {
        if (this.creds != null) {
            return this.creds;
        }
        // '' (empty string) is used as a placeholder for the user if no user is
        // configured. This user can read and write some global configuration.
        final String user = this.user.orElse("");
        final String secret = config.secret().orElseThrow(() -> new IllegalStateException(
                "Using the ExApp-specific " + this.getClass().getName()
                        + " NextcloudAuthProvider requires a server url to be set in the configuration (app.secret)"));
        final String server = this.serverUrl.orElseThrow(() -> new IllegalStateException(
                "Using the ExApp-specific " + this.getClass().getName()
                        + " NextcloudAuthProvider requires a server url to be set in the configuration (nextcloud.url)"));
        this.creds = new NextcloudUserCredentials(user, secret, server, NextcloudUserCredentials.Mode.EXAPP_API);
        return this.creds;
    }
}
