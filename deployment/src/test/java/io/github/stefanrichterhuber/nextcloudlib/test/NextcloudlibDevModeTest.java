package io.github.stefanrichterhuber.nextcloudlib.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.QuarkusUnitTest;

public class NextcloudlibDevModeTest {
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("nextcloud.app-name", "testapp")
            .overrideConfigKey("nextcloud.url", "http://localhost")
            .overrideConfigKey("nextcloud.user", "testuser")
            .overrideConfigKey("nextcloud.password", "secret");
    // Start hot reload (DevMode) test with your extension loaded
    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    public void writeYourOwnDevModeTest() {
        // Write your dev mode tests here - see the testing extension guide
        // https://quarkus.io/guides/writing-extensions#testing-hot-reload for more
        // information
        Assertions.assertTrue(true, "Add dev mode assertions to " + getClass().getName());
    }
}
