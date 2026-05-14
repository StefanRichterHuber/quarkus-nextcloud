package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.util.Base64;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

public interface NextcloudAuthProvider {
    public static final int STANDARD_PRIORITY = 1000;

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
     * User for basic auth header
     *
     * @return
     */
    String getUser();

    /**
     * Password for basic auth header
     *
     * @return
     */
    String getPassword();

    /**
     * Nextcloud URL to connect to, e.g. https://nextcloud.example.com:8080
     *
     * @return
     */
    String getServer();

    /**
     * User for basic auth header
     *
     */
    void setUser(String user);

    /**
     * Password for basic auth header
     *
     */
    void setPassword(String password);

    /**
     * Nextcloud URL to connect to, e.g. https://nextcloud.example.com:8080
     *
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
            this.setPassword(creds.appPassword());
            this.setServer(creds.server());
        } else {
            this.setUser(null);
            this.setPassword(null);
            this.setServer(null);
        }
    }

    /**
     * Returns a Basic-Auth Authorization header build from {@link #getUser()} and
     * {@link #getPassword()}
     * 
     * @return
     */
    default String getAuthorizationHeader() {
        String valueToEncode = getUser() + ":" + getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }
}
