package io.github.stefanrichterhuber.nextcloudlib.other;

import io.github.stefanrichterhuber.nextcloudlib.profiles.ExAppTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ExAppTestProfile.class)
public class NextcloudCalendarTestExApp extends NextcloudCalendarTest {
}
