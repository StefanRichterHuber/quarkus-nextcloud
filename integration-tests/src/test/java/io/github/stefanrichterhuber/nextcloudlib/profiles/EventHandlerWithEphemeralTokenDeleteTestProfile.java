package io.github.stefanrichterhuber.nextcloudlib.profiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class EventHandlerWithEphemeralTokenDeleteTestProfile implements QuarkusTestProfile {
    public static final String PROFILE_TAG = "auth-password-with-event-handler-with-password-delete";

    @Override
    public Map<String, String> getConfigOverrides() {
        // Copy the app password test profile config and override the webhook
        // auto-discovery setting
        final Map<String, String> config = new HashMap<>(new EventHandlerTestProfile().getConfigOverrides());
        config.put("nextcloud.webhook.cleanup-auth-tokens", "true");
        config.put("nextcloud.webhook.cleanup-auth-token-delay", "PT10S");
        return config;
    }

    @Override
    public Set<String> tags() {
        return Set.of(PROFILE_TAG);
    }

}
