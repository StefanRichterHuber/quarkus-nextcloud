package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default {@link NextcloudEventTracker} outside of local dev mode: tracks
 * nothing.
 */
@DefaultBean
@ApplicationScoped
public class NoopNextcloudEventTracker implements NextcloudEventTracker {

    @Override
    public String capture(String handlerId, String rawJson, String eventClass, NextcloudUserCredentials credentials,
            String replayOf, CredentialSource credentialSource) {
        return null;
    }

    @Override
    public void complete(String id, Throwable error) {
    }

    @Override
    public void setStatus(String id, Status status, String error) {
    }

    @Override
    public void markTokenRevoked(String id) {
    }
}
