package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * ExApp-specific
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider}
 * qualified with
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin}.
 * Inherits all credential resolution from {@link ExAppNextcloudAuthProvider}
 * and
 * is injected wherever an admin-scoped provider is required in ExApp mode.
 */
@DefaultBean
@RequestScoped
@Priority(ExAppNextcloudAuthProvider.PRIORITY)
@NextcloudAdmin
public class ExAppNextcloudAdminAuthProvider implements NextcloudAuthProvider {
    /** CDI priority used by this provider — higher than the standard provider. */
    public static final int PRIORITY = NextcloudAuthProvider.STANDARD_PRIORITY + 1000;

    private NextcloudUserCredentials creds = null;

    @Inject
    NextcloudExappAppConfig config;

    @Inject
    @ConfigProperty(name = "nextcloud.url")
    Optional<String> serverUrl;

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
        final String user = "";
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
