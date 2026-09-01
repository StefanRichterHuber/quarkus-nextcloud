package io.github.stefanrichterhuber.nextcloudlib.profiles;

import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class OIDCTestProfile implements QuarkusTestProfile {

    public static final String PROFILE_TAG = "auth-oidc";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "nextcloud.exapp.enabled", "false", //
                "nextcloud.oidc.enabled-for-users", "true", //
                "nextcloud.oidc.enabled-for-admins", "true", //
                "nextcloud.dev-services.enable-oidc", "true", //
                "nextcloud.webhook.build.auto-discovery-enabled", "false", //
                "nextcloud.file-lock-enabled", "true" //
        );
    }

    @Override
    public Set<String> tags() {
        return Set.of(PROFILE_TAG);
    }
}
