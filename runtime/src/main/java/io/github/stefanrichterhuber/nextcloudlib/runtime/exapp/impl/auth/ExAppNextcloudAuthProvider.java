package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * ExApp-specific {@link NextcloudAuthProvider} that resolves credentials from
 * the AppAPI runtime configuration ({@code nextcloud.url}, {@code nextcloud.user},
 * and the shared {@code app.secret}).
 *
 * <p>Registered as a {@link DefaultBean} with a priority 1000 above the standard
 * config-based provider, so it wins the CDI resolution when ExApp mode is active.
 * User and server properties are mutable so that per-request credentials injected
 * by {@link io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppAuthHandler}
 * can override the defaults.
 */
@DefaultBean
@RequestScoped
@Priority(ExAppNextcloudAuthProvider.PRIORITY)
public class ExAppNextcloudAuthProvider implements NextcloudAuthProvider {
    /** CDI priority used by this provider — higher than the standard provider. */
    public static final int PRIORITY = NextcloudAuthProvider.STANDARD_PRIORITY + 1000;

    @Inject
    NextcloudExappAppConfig config;

    @Inject
    @ConfigProperty(name = "nextcloud.url")
    Optional<String> serverUrl;

    @Inject
    @ConfigProperty(name = "nextcloud.user")
    Optional<String> user;

    private Optional<String> password = Optional.empty();

    @Override
    public String getUser() {
        if (user.isPresent()) {
            return user.get();
        }
        // Empty string is the default user
        return "";
    }

    @Override
    public String getPassword() {
        if (password.isPresent()) {
            return password.get();
        }
        return config.secret().get();
    }

    @Override
    public String getServer() {
        return serverUrl.get();
    }

    @Override
    public void setUser(String user) {
        this.user = Optional.ofNullable(user);
    }

    @Override
    public void setPassword(String password) {
        this.password = Optional.ofNullable(password);
    }

    @Override
    public void setServer(String server) {
        this.serverUrl = Optional.ofNullable(server);
    }
}
