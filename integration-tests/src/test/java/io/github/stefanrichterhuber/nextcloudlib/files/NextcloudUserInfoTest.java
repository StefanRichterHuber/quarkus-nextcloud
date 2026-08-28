package io.github.stefanrichterhuber.nextcloudlib.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        NextcloudUser nextcloudUser = userService.getUserInfo(user).orElse(null);
        assertNotNull(nextcloudUser);
        assertEquals(user, nextcloudUser.id());

        NextcloudUser nextcloudUser2 = userService.getCurrentUserInfo().orElse(null);
        assertNotNull(nextcloudUser2);
        assertEquals(user, nextcloudUser2.id());

    }

    @Test
    public void getAppPassword() {
        String user = authProvider.getUser();
        assertNotNull(user);

        NextcloudUserCredentials creds = userService.getAppPassword("internal test app").orElse(null);

        assertEquals(user, creds.loginName());

        // Check if the app password can be used
        Executor exec = new CredentialsAwareRequestScopedExecutor(Executors.newFixedThreadPool(4), creds);
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {

            if (!authProvider.getUser().equals(creds.loginName())) {
                return false;
            }
            if (!authProvider.getCredentials().equals(creds)) {
                return false;
            }

            NextcloudUser nextcloudUser = userService.getCurrentUserInfo().orElse(null);
            if (nextcloudUser == null) {
                return false;
            }
            return true;
        }, exec);
        assertTrue(result.join());
    }
}
