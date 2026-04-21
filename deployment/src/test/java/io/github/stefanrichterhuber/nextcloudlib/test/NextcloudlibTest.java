package io.github.stefanrichterhuber.nextcloudlib.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class NextcloudlibTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest();

    @Test
    public void testExtensionLoads() {
        Assertions.assertTrue(true, "Add unit test assertions to " + getClass().getName());
    }
}
