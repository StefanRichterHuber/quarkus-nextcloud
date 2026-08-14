package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.microprofile.config.ConfigProvider;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.credential.Credential;
import io.smallrye.config.SmallRyeConfig;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

@RegisterForReflection
public record NextcloudUserCredentials(String loginName, String secret, String server, Mode mode)
        implements Credential {

    public static enum Mode {
        /**
         * Use the app password for authentication. Could be the user login password or
         * an app password generated in the nextcloud user settings. This could also be
         * temporary credentials provided by nextcloud events. I any way, authentication
         * is done with Basic Auth with the login name and the password.
         */
        APP_PASSWORD,
        /**
         * Use the OIDC token for authentication.
         */
        OIDC_TOKEN,
        /**
         * Use the exapp api for authentication. In this mode, the secret is provided by
         * the exapp runtime and is not the user's password. It is also independent of
         * the user. Generic requests could be send with the user '' (empty string) and
         * the exapp secret.
         */
        EXAPP_API
    }

    /**
     * Creates a copy of this NextcloudUserCredentials with the specified login
     * name.
     * 
     * @param loginName The login name to set for the new credentials.
     * @return A new instance of NextcloudUserCredentials with the specified login
     *         name.
     */
    public NextcloudUserCredentials withUser(String loginName) {
        return new NextcloudUserCredentials(loginName, this.secret, this.server, this.mode);
    }

    /**
     * Creates a copy of this NextcloudUserCredentials with the specified secret.
     * 
     * @param secret The secret to set for the new credentials.
     * @return A new instance of NextcloudUserCredentials with the specified secret.
     */
    public NextcloudUserCredentials withSecret(String secret) {
        return new NextcloudUserCredentials(this.loginName, secret, this.server, this.mode);
    }

    /**
     * Creates a copy of this NextcloudUserCredentials with the specified
     * nextcloud server.
     * 
     * @param server The nextcloud server to set for the new credentials.
     * @return A new instance of NextcloudUserCredentials with the specified server.
     */
    public NextcloudUserCredentials withServer(String server) {
        return new NextcloudUserCredentials(this.loginName, this.secret, server, this.mode);
    }

    /**
     * Creates a copy of this NextcloudUserCredentials with the specified
     * authentication mode.
     * 
     * @param mode The authentication mode to set for the new credentials.
     * @return A new instance of NextcloudUserCredentials with the specified mode.
     */
    public NextcloudUserCredentials withMode(Mode mode) {
        return new NextcloudUserCredentials(this.loginName, this.secret, this.server, mode);
    }

    /**
     * Returns the required headers for the current authentication mode.
     * 
     * @return Map of required headers for the current authentication mode. Usually
     *         at least contains a
     *         "OCS-APIRequest": "true" header.
     */
    public MultivaluedMap<String, String> getRequiredHeaders() {
        if (this.mode() == Mode.EXAPP_API) {
            final SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
            final NextcloudExappAppConfig appConfig = config.getConfigMapping(NextcloudExappAppConfig.class);

            final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            headers.add("OCS-APIRequest", "true");

            headers.putSingle("EX-APP-ID", appConfig.id());
            headers.putSingle("EX-APP-VERSION", appConfig.version());
            headers.putSingle("OCS-APIRequest", "true");
            headers.putSingle("User-Agent", appConfig.id());

            final String user = loginName();
            final String secret = appConfig.secret().get();
            final String auth = user + ":" + secret;
            final String authHeader = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.putSingle("AUTHORIZATION-APP-API", authHeader);
            return headers;
        } else {
            final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            headers.add("OCS-APIRequest", "true");

            if (this.mode() == Mode.OIDC_TOKEN) {
                headers.putSingle("Authorization", "Bearer " + this.secret());
            } else {
                final String valueToEncode = loginName() + ":" + secret();
                headers.putSingle("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes()));
            }
            return headers;
        }
    }
}