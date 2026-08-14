package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudUserServiceTest {

    @Inject
    NextcloudUserService userService;

    @Test
    public void testGetCurrentUserInfo() {
        NextcloudUser userInfo = userService.getCurrentUserInfo().get();
        assertNotNull(userInfo);
    }
}
