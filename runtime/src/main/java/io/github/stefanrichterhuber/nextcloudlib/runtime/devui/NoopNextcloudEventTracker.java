package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Reserve;

/**
 * Default {@link NextcloudEventTracker} outside of local dev mode: tracks
 * nothing.
 */
@Reserve
@Priority(0)
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
