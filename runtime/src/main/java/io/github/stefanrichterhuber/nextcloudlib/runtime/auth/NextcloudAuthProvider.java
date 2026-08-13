package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.microprofile.config.ConfigProvider;

import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudOIDCConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.smallrye.config.SmallRyeConfig;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

/**
 * CDI Beans implementing this interface are used to provide location of the
 * nextcloud server and credentials for the current nextcloud user
 */
public interface NextcloudAuthProvider {
    public static final int STANDARD_PRIORITY = 1000;

    public static enum Mode {
        /**
         * Use the app password for authentication
         */
        APP_PASSWORD,
        /**
         * Use the OIDC token for authentication
         */
        OIDC_TOKEN,
        /**
         * Use the exapp api for authentication
         */
        EXAPP_API
    }

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
    default Mode getMode() {
        final SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        if (config.getConfigMapping(NextcloudExappConfig.class).enabled()) {
            return Mode.EXAPP_API;
        } else if (config.getConfigMapping(NextcloudOIDCConfig.class).enabledForUsers()) {
            return Mode.OIDC_TOKEN;
        } else {
            return Mode.APP_PASSWORD;
        }
    }

    /**
     * Additional headers for the request. Usually at least containes a
     * "OCS-APIRequest": "true"
     *
     * @return
     */
    default MultivaluedMap<String, String> getCustomHeaders() {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("OCS-APIRequest", "true");
        return headers;
    }

    /**
     * Current user
     *
     * @return current user
     */
    String getUser();

    /**
     * Current Password / Access token for the current user. If OIDC is enabled,
     * this will be the OIDC token instead of the app password.
     * 
     * @return Password / Token of the current user
     */
    String getSecret();

    /**
     * Nextcloud URL to connect to, e.g. https://nextcloud.example.com:8080
     *
     * @return Nextcloud server
     */
    String getServer();

    /**
     * Set the current user
     * 
     * @param user User to set
     */
    void setUser(String user);

    /**
     * Set the password for basic auth header / OIDC token for authentication. If
     * OIDC is enabled, this will be the OIDC token instead of the app password.
     * 
     * @param password Password to set
     */
    void setSecret(String password);

    /**
     * Nextcloud URL to connect to, e.g. https://nextcloud.example.com:8080
     * 
     * @param server Server to set
     */
    void setServer(String server);

    /**
     * Configures this NextcloudAuthProvider from {@link NextcloudUserCredentials}
     * 
     * @param creds If null, user, password and server is set to null
     */
    default void setCredentials(NextcloudUserCredentials creds) {
        if (creds != null) {
            this.setUser(creds.loginName());
            this.setSecret(creds.secret());
            this.setServer(creds.server());
        } else {
            this.setUser(null);
            this.setSecret(null);
            this.setServer(null);
        }
    }

    /**
     * Returns the {@link NextcloudUserCredentials} for the current user
     */
    default NextcloudUserCredentials getCredentials() {
        return new NextcloudUserCredentials(this.getUser(), this.getSecret(), this.getServer());
    }

    /**
     * Returns the required headers for the current authentication mode.
     * 
     * @return Map of required headers for the current authentication mode. Usually
     *         at least contains a
     *         "OCS-APIRequest": "true" header.
     */
    default MultivaluedMap<String, String> getRequiredHeaders() {
        if (this.getMode() == Mode.EXAPP_API) {
            final SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
            final NextcloudExappAppConfig appConfig = config.getConfigMapping(NextcloudExappAppConfig.class);

            final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            headers.putAll(getCustomHeaders());
            headers.add("OCS-APIRequest", "true");

            headers.putSingle("EX-APP-ID", appConfig.id());
            headers.putSingle("EX-APP-VERSION", appConfig.version());
            headers.putSingle("OCS-APIRequest", "true");
            headers.putSingle("User-Agent", appConfig.id());

            final String user = getUser();
            final String secret = appConfig.secret().get();
            final String auth = user + ":" + secret;
            final String authHeader = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.putSingle("AUTHORIZATION-APP-API", authHeader);
            return headers;
        } else {
            final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            headers.putAll(getCustomHeaders());
            headers.add("OCS-APIRequest", "true");

            if (this.getMode() == Mode.OIDC_TOKEN) {
                headers.putSingle("Authorization", "Bearer " + this.getSecret());
            } else {
                final String valueToEncode = getUser() + ":" + getSecret();
                headers.putSingle("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes()));
            }
            return headers;
        }
    }
}
