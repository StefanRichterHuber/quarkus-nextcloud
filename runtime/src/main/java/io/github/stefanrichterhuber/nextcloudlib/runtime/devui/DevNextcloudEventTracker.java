package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudDevEventStore.Entry;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudDevEventStore.EventSnapshot;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * {@link NextcloudEventTracker} of local dev mode: records the events in the
 * static {@link NextcloudDevEventStore}, so they survive hot reloads. Only
 * registered in local dev mode, overriding the {@link NoopNextcloudEventTracker}.
 */
@ApplicationScoped
public class DevNextcloudEventTracker implements NextcloudEventTracker {

    /**
     * Maximum number of events kept
     */
    @ConfigProperty(name = "nextcloud.dev-ui.max-events", defaultValue = "100")
    int maxEvents;

    @Override
    public String capture(String handlerId, String rawJson, String eventClass, NextcloudUserCredentials credentials,
            String replayOf, CredentialSource credentialSource) {
        return NextcloudDevEventStore.capture(handlerId, rawJson, eventClass, credentials, replayOf,
                credentialSource, maxEvents);
    }

    @Override
    public void complete(String id, Throwable error) {
        NextcloudDevEventStore.complete(id, error);
    }

    @Override
    public void setStatus(String id, Status status, String error) {
        NextcloudDevEventStore.setStatus(id, status, error);
    }

    @Override
    public void markTokenRevoked(String id) {
        NextcloudDevEventStore.markTokenRevoked(id);
    }

    /**
     * @return All captured events, newest first
     */
    List<EventSnapshot> list() {
        return NextcloudDevEventStore.list();
    }

    /**
     * @param id Id of the event
     * @return The captured event including its credentials
     */
    Optional<Entry> get(String id) {
        return NextcloudDevEventStore.get(id);
    }

    /**
     * Removes all captured events
     */
    void clear() {
        NextcloudDevEventStore.clear();
    }

    void addListener(Consumer<EventSnapshot> listener) {
        NextcloudDevEventStore.addListener(listener);
    }

    void removeListener(Consumer<EventSnapshot> listener) {
        NextcloudDevEventStore.removeListener(listener);
    }
}
