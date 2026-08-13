package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.security.Principal;

import org.eclipse.microprofile.config.ConfigProvider;

import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudOIDCConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.quarkus.arc.DefaultBean;
import io.quarkus.security.credential.TokenCredential;
import io.smallrye.config.SmallRyeConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;

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
public class ConfiguredNextcloudAdminAuthProvider extends ConfiguredNextcloudAuthProvider {

    /**
     * Returns the current authentication mode. The default implementation checks
     * the
     * configuration for the nextcloud.exapp.enabled and nextcloud.oidc.enabled
     * flags
     * and returns the appropriate mode. If both are disabled, it returns
     * {@link Mode#APP_PASSWORD}
     * 
     * @return
     */
    public Mode getMode() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        if (config.getConfigMapping(NextcloudExappConfig.class).enabled()) {
            return Mode.EXAPP_API;
        } else if (config.getConfigMapping(NextcloudOIDCConfig.class).enabledForAdmins()) {
            return Mode.OIDC_TOKEN;
        } else {
            return Mode.APP_PASSWORD;
        }
    }

    @Override
    public String getUser() {
        if (oidcConfig.enabledForAdmins() && !securityIdentity.isAnonymous()) {
            final Principal principal = securityIdentity.getPrincipal();
            if (principal != null && principal.getName() != null && !principal.getName().isEmpty()) {
                return principal.getName();
            }
        }
        return user
                .orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                        + " NextcloudAuthProvider requires a user to be set in the configuration (nextcloud.user)"));

    }

    @Override
    public String getSecret() {
        if (oidcConfig.enabledForAdmins() && !securityIdentity.isAnonymous()) {
            final TokenCredential cred = securityIdentity.getCredential(TokenCredential.class);
            if (cred != null) {
                return cred.getToken();
            }
        }
        return password
                .orElseThrow(() -> new IllegalStateException("Using the default " + this.getClass().getName()
                        + " NextcloudAuthProvider requires a password to be set in the configuration (nextcloud.password)"));
    }
}
