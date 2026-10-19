package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * The dev service of a {@code @QuarkusIntegrationTest} is started in the test
 * JVM, which does not see the build profile of the binary. This profile tells
 * it to set up AppAPI and register the binary as ExApp.
 */
public class ExAppDevServiceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("nextcloud.dev-services.enable-ex-app", "true");
    }
}
