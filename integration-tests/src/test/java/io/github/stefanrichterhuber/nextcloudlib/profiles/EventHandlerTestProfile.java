package io.github.stefanrichterhuber.nextcloudlib.profiles;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class EventHandlerTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "nextcloud.exapp.enabled", "false", //
                "nextcloud.oidc.enabled-for-users", "false", //
                "nextcloud.oidc.enabled-for-admins", "false", //
                "nextcloud.dev-services.enable-oidc", "false", //
                "nextcloud.webhook.build.auto-discovery-enabled", "true"//
        );
    }

}
