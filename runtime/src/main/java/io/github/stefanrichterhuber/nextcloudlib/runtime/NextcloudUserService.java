package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudSecurityIdentity;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudAdminRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient.GetAppPasswordResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials.Mode;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Service for managing Nextcloud users.
 */
@ApplicationScoped
public class NextcloudUserService {
    @Inject
    NextcloudAuthProvider authProvider;

    @Inject
    Logger log;

    /**
     * Get information about the current user. This is a convenient method that
     * internally calls {@link #getUserInfo(String)} with the user from the auth
     * provider.
     * 
     * @return Information about the current user, or an empty Optional if the user
     *         is not found
     */
    public NextcloudUser getCurrentUserInfo() {
        return getUserInfo(authProvider.getUser());
    }

    /**
     * Get information about a user by their username. This is usually only
     * available for the current user or for administrators.
     * 
     * @param user Username of the user to get information about
     * @return Information about the current user, or null if the user is not found
     */
    public NextcloudUser getUserInfo(String user) {
        if (user == null || user.isBlank()) {
            return null;
        }
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        try {
            final OCSMessage<NextcloudUser> response = client.getUserInfo(user);
            if (response.isOk()) {
                return response.ocs().data();
            } else {
                return null;
            }
        } catch (WebApplicationException e) {
            log.debugf(e, "Failed to fetch infos for user %s", user);
            return null;
        }
    }

    /**
     * Creates a SecurityContext from a NextcloudUser with credentials
     * 
     * @param user        user
     * @param credentials Credentials of the user
     * @return SecurityContext created
     */
    public SecurityContext getSecurityContext(NextcloudUser user, NextcloudUserCredentials credentials) {
        return new NextcloudSecurityIdentity(user, credentials, null);
    }

    /**
     * Creates a SecurityContext from a NextcloudUser without credentials
     * 
     * @param user user
     * @return
     */
    public SecurityContext getSecurityContext(NextcloudUser user) {
        return getSecurityContext(user, null);
    }

    /**
     * Creates a SecurityContext from a NextcloudUser with credentials
     * 
     * @param user        user
     * @param credentials Credentials
     * @return SecurityContext created
     */
    public SecurityContext getSecurityContext(String user, NextcloudUserCredentials credentials) {
        final NextcloudUser userInfo = getUserInfo(user);
        return userInfo != null ? getSecurityContext(userInfo, credentials) : null;
    }

    /**
     * Creates a SecurityContext from a NextcloudUser without credentials
     * 
     * @param user user
     * @return SecurityContext created
     */
    public SecurityContext getSecurityContext(String user) {
        return getSecurityContext(user, null);
    }

    /**
     * Creates a SecurityContext for the current user without credentials
     * 
     * @return
     */
    public SecurityContext getSecurityContextForCurrentUser() {
        return getSecurityContext(authProvider.getUser(), authProvider.getCredentials());
    }

    /**
     * Creates a SecurityIdentity from a NextcloudUser with credentials
     * 
     * @param user        user
     * @param credentials credentials
     * @return Security Identity
     */
    public SecurityIdentity getSecurityIdentity(NextcloudUser user, NextcloudUserCredentials credentials) {
        return new NextcloudSecurityIdentity(user, credentials, null);
    }

    /**
     * Creates an app password for the current user
     * 
     * @param applicationName Name of this application (shown in the app password
     *                        settings), Required
     * @return Credentials
     */
    public NextcloudUserCredentials getAppPassword(String applicationName) {
        if (applicationName == null || applicationName.isBlank()) {
            throw new IllegalArgumentException("applicationName must not be null or empty");
        }

        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);
        try {
            final OCSMessage<GetAppPasswordResult> result = client.getAppPassword(applicationName);
            if (result.isOk()) {
                final String secret = result.ocs().data().apppassword();
                final String user = authProvider.getUser();
                final String server = authProvider.getServer();

                return (new NextcloudUserCredentials(user, secret, server, Mode.APP_PASSWORD));
            } else {
                return null;
            }
        } catch (WebApplicationException e) {
            log.errorf(e, "Failed to retrieve app password for user %s -> consider account deleted anyway",
                    authProvider.getUser());
            return null;
        }
    }

    /**
     * Rotates the app password of the given user idenity
     * 
     * @param credentials Credentials contianing the password to rotate
     * @return New Credentials for the same user but with a new app password
     */
    public NextcloudUserCredentials rotateAppPassword(NextcloudUserCredentials credentials) {
        if (credentials == null) {
            throw new NullPointerException("credentials must not be null");
        }
        if (credentials.mode() != NextcloudUserCredentials.Mode.APP_PASSWORD) {
            throw new IllegalArgumentException(String
                    .format("Given credentials are of mode %s. This could not be an app password", credentials.mode()));
        }

        final String server = credentials.server();
        final String user = credentials.loginName();
        final String secret = credentials.secret();

        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(server))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        final String valueToEncode = user + ":" + secret;
        final String authHeader = "Basic "
                + Base64.getEncoder().encodeToString(valueToEncode.getBytes(StandardCharsets.UTF_8));

        try {
            final OCSMessage<GetAppPasswordResult> r = client.rotateAppPassword(authHeader);
            if (r.isOk()) {
                log.debugf("Successfully rotated app password for user %s", user);
                return new NextcloudUserCredentials(user, r.ocs().data().apppassword(), server, Mode.APP_PASSWORD);
            } else {
                log.errorf("Failed to rotate app password for user %s", user);
                return null;
            }
        } catch (WebApplicationException e) {
            log.errorf("Failed to rotate app password for user %s", user);
            return null;
        }
    }

    /**
     * Deletes the given app password for the given user identity
     * 
     * @param credentials Password to delete, must be non-empty
     * @return Success of the operation
     */
    public boolean deleteAppPassword(final NextcloudUserCredentials credentials) {
        if (credentials == null) {
            throw new NullPointerException("credentials must not be null");
        }
        if (credentials.mode() != NextcloudUserCredentials.Mode.APP_PASSWORD) {
            throw new IllegalArgumentException(String
                    .format("Given credentials are of mode %s. This could not be an app password", credentials.mode()));
        }

        final String server = credentials.server();
        final String user = credentials.loginName();
        final String secret = credentials.secret();

        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(server))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        final String valueToEncode = user + ":" + secret;
        final String authHeader = "Basic "
                + Base64.getEncoder().encodeToString(valueToEncode.getBytes(StandardCharsets.UTF_8));

        try {
            final OCSMessage<Object> r = client.deleteAppPassword(authHeader);
            if (r.isOk()) {
                log.debugf("Successfully deleted app password for user %s", user);
            } else {
                log.errorf("Failed to deleted app password for user %s -> consider app password deleted anyway", user);
                return false;
            }
        } catch (WebApplicationException e) {
            log.errorf(e, "Failed to deleted app password for user %s -> consider app password deleted anyway", user);
            return false;
        }
        // If a non 200 status code is returned the client should still proceed with
        // removing the account.
        return true;
    }

    /**
     * Lists all users on the Nextcloud server. This method requires administrative
     * privileges (NextcloudAuthProvider with {@code @NextcloudAdmin} qualifier).
     * 
     * @param search Optional search string to filter users
     * @param limit  Optional limit of users to return
     * @param offset Optional offset for pagination
     * @return List of usernames, or an empty list if the operation fails or no
     *         users are found
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/admin_manual/configuration_user/instruction_set_for_users.html">Nextcloud
     *      Admin Manual: Instruction Set for Users</a>
     */
    public List<String> listUsers(String search, Integer limit, Integer offset) {
        final NextcloudAdminRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudAdminRestClient.class);

        try {
            final OCSMessage<NextcloudAdminRestClient.UserListResponse> response = client.listUsers(search, limit,
                    offset);
            if (response.isOk()) {
                return response.ocs().data().users();
            } else {
                return Collections.emptyList();
            }
        } catch (WebApplicationException e) {
            log.debugf(e, "Failed to fetch user list");
            return Collections.emptyList();
        }
    }

    /**
     * Creates a new user on the Nextcloud server. This method requires
     * administrative
     * privileges (NextcloudAuthProvider with {@code @NextcloudAdmin} qualifier).
     * 
     * @param userId      the required username for the new user, required
     * @param password    the password for the new user, leave empty to send welcome
     *                    mail
     * @param displayName the display name for the new user
     * @param email       the email for the new user, required if password empty
     * @param groups      the groups for the new user
     * @param subAdmin    the groups in which the new user is sub admin
     * @param quota       the quota for the new user, leave empty for default
     * @param language    the language for the new user
     * @return Success of the operation
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/admin_manual/configuration_user/instruction_set_for_users.html">Nextcloud
     *      Admin Manual: Instruction Set for Users</a>
     */
    public boolean createUser(@FormParam("userid") String userId,
            String password,
            String displayName,
            String email,
            List<String> groups,
            List<String> subAdmin,
            String quota,
            String language) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or empty");
        }
        if (password == null || password.isBlank()) {
            // Either password or email must be provided
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Either password or email must be provided");
            }
        }

        final NextcloudAdminRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudAdminRestClient.class);

        try {
            final OCSMessage<NextcloudAdminRestClient.UserInfoResponse> response = client.createUser(userId, password,
                    displayName, email, groups, subAdmin, quota, language);
            if (response.isOk()) {
                log.debugf("Successfully created user %s", userId);
                return true;
            } else {
                log.errorf("Failed to create user %s", userId);
                return false;
            }
        } catch (WebApplicationException e) {
            log.errorf(e, "Failed to create user %s", userId);
            return false;
        }
    }

    /**
     * Deletes a user by their username. This method requires administrative
     * privileges (NextcloudAuthProvider with {@code @NextcloudAdmin} qualifier).
     * 
     * @param user Username of the user to delete
     * @return Success of the operation
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/admin_manual/configuration_user/instruction_set_for_users.html">Nextcloud
     *      Admin Manual: Instruction Set for Users</a>
     */
    public boolean deleteUser(String user) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be null or empty");
        }
        final NextcloudAdminRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudAdminRestClient.class);

        try {
            final OCSMessage<Object> response = client.deleteUser(user);
            if (response.isOk()) {
                log.debugf("Successfully deleted user %s", user);
                return true;
            } else {
                log.errorf("Failed to delete user %s", user);
                return false;
            }
        } catch (WebApplicationException e) {
            log.errorf(e, "Failed to delete user %s", user);
            return false;
        }
    }

    /**
     * Enables a user by their username. This method requires administrative
     * privileges (NextcloudAuthProvider with {@code @NextcloudAdmin} qualifier).
     * 
     * @param user Username of the user to enable
     * @return
     * 
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/admin_manual/configuration_user/instruction_set_for_users.html">Nextcloud
     *      Admin Manual: Instruction Set for Users</a>
     */
    public boolean enableUser(String user) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be null or empty");
        }
        final NextcloudAdminRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudAdminRestClient.class);

        try {
            final OCSMessage<Object> response = client.enableUser(user);
            if (response.isOk()) {
                log.debugf("Successfully enabled user %s", user);
                return true;
            } else {
                log.errorf("Failed to enable user %s", user);
                return false;
            }
        } catch (WebApplicationException e) {
            log.errorf(e, "Failed to enable user %s", user);
            return false;
        }
    }

    /**
     * Disables a user by their username. This method requires administrative
     * privileges (NextcloudAuthProvider with {@code @NextcloudAdmin} qualifier).
     * 
     * @param user Username of the user to disable
     * @return
     * @see <a href=
     *      "https://docs.nextcloud.com/server/latest/admin_manual/configuration_user/instruction_set_for_users.html">Nextcloud
     *      Admin Manual: Instruction Set for Users</a>
     */
    public boolean disableUser(String user) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be null or empty");
        }
        final NextcloudAdminRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudAdminRestClient.class);

        try {
            final OCSMessage<Object> response = client.disableUser(user);
            if (response.isOk()) {
                log.debugf("Successfully disabled user %s", user);
                return true;
            } else {
                log.errorf("Failed to disable user %s", user);
                return false;
            }
        } catch (WebApplicationException e) {
            log.errorf(e, "Failed to disable user %s", user);
            return false;
        }
    }

}
