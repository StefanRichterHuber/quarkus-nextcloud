package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Default implementation of NextcloudAuthProvider that reads the credentials
 * from the configuration. This bean is only used if no custom
 * NextcloudAuthProvider is provided by the user. The required configuration
 * properties are:
 * <ul>
 * <li>nextcloud.url: Root url of the nextcloud installation (e.g.
 * 'https://nextcloud.example.com')</li>
 * <li>nextcloud.appName: Name of this application (required to get the correct
 * app password for the nextcloud rest api)</li>
 * <li>nextcloud.user: Username for the nextcloud installation</li>
 * <li>nextcloud.password: Password for the nextcloud installation</li>
 * </ul>
 */
@DefaultBean
@ApplicationScoped
public class ConfiguredNextcloudAuthProvider implements NextcloudAuthProvider {
    @Inject
    @ConfigProperty(name = "nextcloud.url")
    Optional<String> serverUrl;

    @Inject
    @ConfigProperty(name = "nextcloud.user")
    Optional<String> user;

    @Inject
    @ConfigProperty(name = "nextcloud.password")
    Optional<String> password;

    @Override
    public String getUser() {
        return user
                .orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                        + " NextcloudAuthProvider requires a user to be set in the configuration (nextcloud.user)"));

    }

    @Override
    public String getPassword() {
        return password
                .orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                        + " NextcloudAuthProvider requires a password to be set in the configuration (nextcloud.password)"));

    }

    @Override
    public String getServer() {
        return serverUrl.orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                + " NextcloudAuthProvider requires a server url to be set in the configuration (nextcloud.url)"));
    }

}
