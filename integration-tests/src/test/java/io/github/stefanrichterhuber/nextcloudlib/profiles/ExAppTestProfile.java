package io.github.stefanrichterhuber.nextcloudlib.profiles;

import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ExAppTestProfile implements QuarkusTestProfile {

    public static final String PROFILE_TAG = "auth-exapp";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "nextcloud.exapp.enabled", "true", //
                "nextcloud.oidc.enabled-for-users", "false", //
                "nextcloud.oidc.enabled-for-admins", "false", //
                "nextcloud.dev-services.enable-oidc", "false", //
                "nextcloud.webhook.build.auto-discovery-enabled", "false"//
        );
    }

    @Override
    public Set<String> tags() {
        return Set.of(PROFILE_TAG);
    }
}
