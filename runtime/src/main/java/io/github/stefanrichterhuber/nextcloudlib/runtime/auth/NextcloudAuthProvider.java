package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;

/**
 * CDI Beans implementing this interface are used to provide location of the
 * nextcloud server and credentials for the current nextcloud user
 */
public interface NextcloudAuthProvider {
    public static final int STANDARD_PRIORITY = 1000;

    /**
     * Configures this NextcloudAuthProvider from {@link NextcloudUserCredentials}
     * 
     * @param creds If null, user, password and server is set to null
     */
    void setCredentials(NextcloudUserCredentials creds);

    /**
     * Returns the {@link NextcloudUserCredentials} for the current user
     */
    NextcloudUserCredentials getCredentials();

    /**
     * Return the current user's login name. This is a convenience method that
     * delegates to {@link #getCredentials()}.
     * 
     * @return the nextcloud login name
     */
    default String getUser() {
        return getCredentials().loginName();
    }

    /**
     * Returns the current users nextcloud server url. This is a convenience method
     * that delegates to {@link #getCredentials()}.
     * 
     * @return the nextcloud server url
     */
    default String getServer() {
        return getCredentials().server();
    }

}
