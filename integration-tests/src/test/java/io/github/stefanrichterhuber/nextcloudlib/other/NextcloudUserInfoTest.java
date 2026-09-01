package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudUserInfoTest {

    @Inject
    NextcloudUserService userService;

    @Inject
    NextcloudAuthProvider authProvider;

    @Test
    public void getUserInfo() {
        String user = authProvider.getUser();
        assertNotNull(user);

        NextcloudUser nextcloudUser = userService.getUserInfo(user);
        assertNotNull(nextcloudUser);
        assertEquals(user, nextcloudUser.id());

        NextcloudUser nextcloudUser2 = userService.getCurrentUserInfo();
        assertNotNull(nextcloudUser2);
        assertEquals(user, nextcloudUser2.id());

    }

    @Test
    public void getAppPassword() {
        String user = authProvider.getUser();
        assertNotNull(user);

        NextcloudUserCredentials creds = userService.getAppPassword("internal test app");

        assertNotNull(creds);
        assertEquals(user, creds.loginName());
        // Check if the app password can be used
        final Executor exec = new CredentialsAwareRequestScopedExecutor(Executors.newFixedThreadPool(4), creds);
        final CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {

            if (!authProvider.getUser().equals(creds.loginName())) {
                return false;
            }
            if (!authProvider.getCredentials().equals(creds)) {
                return false;
            }

            NextcloudUser nextcloudUser = userService.getCurrentUserInfo();
            if (nextcloudUser == null) {
                return false;
            }
            return true;
        }, exec);
        assertTrue(result.join());

        assertTrue(userService.deleteAppPassword(creds));

        assertFalse(userService.deleteAppPassword(creds.withSecret("testvalue")));

    }

    @Test
    public void rotateAppPassword() {
        String user = authProvider.getUser();
        assertNotNull(user);

        NextcloudUserCredentials creds = userService.getAppPassword("internal test app");
        assertNotNull(creds);
        assertEquals(user, creds.loginName());

        // Check if the app password can be used
        {
            final Executor exec = new CredentialsAwareRequestScopedExecutor(Executors.newFixedThreadPool(4), creds);
            final CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {

                if (!authProvider.getCredentials().equals(creds)) {
                    return false;
                }

                NextcloudUser nextcloudUser = userService.getCurrentUserInfo();
                if (nextcloudUser == null) {
                    return false;
                }
                return true;
            }, exec);
            assertTrue(result.join());
        }

        NextcloudUserCredentials rotatedCreds = userService.rotateAppPassword(creds);
        assertNotNull(rotatedCreds);
        assertEquals(user, rotatedCreds.loginName());
        assertEquals(creds.server(), rotatedCreds.server());

        assertNotEquals(creds.secret(), rotatedCreds.secret());

        // Check if the rotated app password can be used
        {
            final Executor exec = new CredentialsAwareRequestScopedExecutor(Executors.newFixedThreadPool(4),
                    rotatedCreds);
            final CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {

                if (!authProvider.getCredentials().equals(rotatedCreds)) {
                    return false;
                }

                final NextcloudUser nextcloudUser = userService.getCurrentUserInfo();
                if (nextcloudUser == null) {
                    return false;
                }
                return true;
            }, exec);
            assertTrue(result.join());
        }

        assertTrue(userService.deleteAppPassword(rotatedCreds));

        // Old app password should no longer be valids
        assertFalse(userService.deleteAppPassword(creds));

    }
}
