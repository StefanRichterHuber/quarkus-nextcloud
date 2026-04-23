package io.github.stefanrichterhuber.nextcloudlib.users;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class NextcloudUserServiceTest {

    @Inject
    NextcloudUserService userService;

    @Test
    public void testGetCurrentUserInfo() {
        NextcloudUser userInfo = userService.getCurrentUserInfo().get();
        System.out.println(userInfo);
    }
}
