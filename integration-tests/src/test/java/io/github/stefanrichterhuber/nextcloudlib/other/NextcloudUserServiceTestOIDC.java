package io.github.stefanrichterhuber.nextcloudlib.other;

import io.github.stefanrichterhuber.nextcloudlib.profiles.OIDCTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OIDCTestProfile.class)
public class NextcloudUserServiceTestOIDC extends NextcloudUserServiceTest {
}
