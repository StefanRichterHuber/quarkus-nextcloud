package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.security.Principal;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudOIDCConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials.Mode;
import io.quarkus.arc.DefaultBean;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
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
@RequestScoped
@NextcloudAdmin
@Priority(NextcloudAuthProvider.STANDARD_PRIORITY)
public class ConfiguredNextcloudAdminAuthProvider implements NextcloudAuthProvider {

    @Inject
    @ConfigProperty(name = "nextcloud.url")
    Optional<String> serverUrl;

    @Inject
    @ConfigProperty(name = "nextcloud.user")
    Optional<String> user;

    @Inject
    @ConfigProperty(name = "nextcloud.password")
    Optional<String> password;

    @Inject
    @ConfigProperty(name = "nextcloud.admin-user")
    Optional<String> adminUser;

    @Inject
    @ConfigProperty(name = "nextcloud.admin-password")
    Optional<String> adminPassword;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    NextcloudOIDCConfig oidcConfig;

    private NextcloudUserCredentials creds = null;

    @Override
    public NextcloudUserCredentials getCredentials() {
        if (creds == null) {
            String user = null;
            String secret = null;
            final Mode mode = oidcConfig.enabledForAdmins() ? NextcloudUserCredentials.Mode.OIDC_TOKEN
                    : NextcloudUserCredentials.Mode.APP_PASSWORD;
            final String server = serverUrl.orElseThrow(() -> new IllegalStateException("Using the default "
                    + this.getClass().getName()
                    + " NextcloudAuthProvider requires a server url to be set in the configuration (nextcloud.url)"));
            if (mode == NextcloudUserCredentials.Mode.OIDC_TOKEN && !securityIdentity.isAnonymous()) {
                // If OIDC is enabled for admins, we take the user from the security identity
                // (so the current users session)
                final Principal principal = securityIdentity.getPrincipal();
                final TokenCredential cred = securityIdentity.getCredential(TokenCredential.class);
                user = principal.getName();
                secret = cred.getToken();
            } else {
                user = this.adminUser.or(() -> this.user)
                        .orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                                + " NextcloudAuthProvider requires a user to be set in the configuration (nextcloud.admin-user or nextcloud.user)"));
                secret = this.adminPassword.or(() -> this.password)
                        .orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                                + " NextcloudAuthProvider requires a password to be set in the configuration (nextcloud.admin-password or nextcloud.password)"));
            }

            creds = new NextcloudUserCredentials(user, secret, server, mode);
        }
        return creds;
    }

    @Override
    public void setCredentials(NextcloudUserCredentials creds) {
        this.creds = creds;
    }

}
