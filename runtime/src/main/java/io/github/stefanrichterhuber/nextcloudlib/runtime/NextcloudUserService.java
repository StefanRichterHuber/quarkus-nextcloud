package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.net.URI;
import java.util.Optional;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudSecurityIdentity;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Service for managing Nextcloud users.
 */
@ApplicationScoped
public class NextcloudUserService {
    @Inject
    NextcloudAuthProvider authProvider;

    /**
     * Get information about the current user. This is a convenient method that
     * internally calls {@link #getUserInfo(String)} with the user from the auth
     * provider.
     * 
     * @return Information about the current user, or an empty Optional if the user
     *         is not found
     */
    public Optional<NextcloudUser> getCurrentUserInfo() {
        return getUserInfo(authProvider.getUser());
    }

    /**
     * Get information about a user by their username. This is usually only
     * available for the current user or for administrators.
     * 
     * @param user Username of the user to get information about
     * @return Information about the current user, or an empty Optional if the user
     *         is not found
     */
    public Optional<NextcloudUser> getUserInfo(String user) {
        if (user == null || user.isBlank()) {
            return Optional.empty();
        }
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        final OCSMessage<NextcloudUser> response = client.getUserInfo(user);
        if (response.isOk()) {
            return Optional.ofNullable(response.ocs().data());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Creates a SecurityContext from a NextcloudUser
     * 
     * @param user user
     * @return
     */
    public SecurityContext getSecurityContext(NextcloudUser user) {
        return new NextcloudSecurityIdentity(user, null, null);
    }

    /**
     * Creates a SecurityContext from a NextcloudUser
     * 
     * @param user user
     * @return
     */
    public SecurityContext getSecurityContext(String user) {
        return getUserInfo(user).map(u -> new NextcloudSecurityIdentity(u, null, null)).orElse(null);
    }

    /**
     * Creates a SecurityContext for the current user
     * 
     * @return
     */
    public SecurityContext getSecurityContextForCurrentUser() {
        return getSecurityContext(authProvider.getUser());
    }

    /**
     * Creates a SecurityIdentity from a NextcloudUser
     * 
     * @param user        user
     * @param credentials credentials
     * @return
     */
    public SecurityIdentity getSecurityIdentity(NextcloudUser user, NextcloudUserCredentials credentials) {
        return new NextcloudSecurityIdentity(user, credentials, null);
    }
}
