package io.github.stefanrichterhuber.nextcloudlib.profiles;

import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class AppPasswordTestProfile implements QuarkusTestProfile {
    public static final String PROFILE_TAG = "auth-password";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "nextcloud.exapp.enabled", "false", //
                "nextcloud.oidc.enabled-for-users", "false", //
                "nextcloud.oidc.enabled-for-admins", "false", //
                "nextcloud.webhook.build.auto-discovery-enabled", "false"//
        );
    }

    @Override
    public Set<String> tags() {
        return Set.of(PROFILE_TAG);
    }
}
