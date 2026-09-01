package io.github.stefanrichterhuber.nextcloudlib.files;

import io.github.stefanrichterhuber.nextcloudlib.profiles.ExAppTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ExAppTestProfile.class)
public class NextcloudFileCommentTestExApp extends NextcloudFileCommentTest {

}
