package io.github.stefanrichterhuber.nextcloudlib.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestEventHandlers {
    /**
     * Directory whose creation is handled by {@link #onFileEventAsyncStage}
     */
    public static final String ASYNC_STAGE_DIR = "/TESTDIR_ASYNC_STAGE_HANDLER";
    /**
     * Directory whose creation is handled by {@link #onFileEventAsyncUni}
     */
    public static final String ASYNC_UNI_DIR = "/TESTDIR_ASYNC_UNI_HANDLER";

    /**
     * Event received by an asynchronous handler, which is only finished when the
     * test completes {@link #completion()}.
     *
     * @param event       Received event
     * @param credentials Ephemeral credentials delivered with the event
     * @param completion  Completing this future finishes the handler
     */
    public record PendingAsyncEvent(NextcloudEvent<NextcloudEvent.FileEvent> event,
            NextcloudUserCredentials credentials, CompletableFuture<Void> completion) {
    }

    private final List<NextcloudEvent<NextcloudEvent.FileEvent>> receivedFileEvents = new CopyOnWriteArrayList<>();
    private final List<NextcloudEvent<NextcloudEvent.SystemTagEvent>> receivedSystemTagEvents = new CopyOnWriteArrayList<>();
    private final List<PendingAsyncEvent> asyncStageEvents = new CopyOnWriteArrayList<>();
    private final List<PendingAsyncEvent> asyncUniEvents = new CopyOnWriteArrayList<>();

    @OnNextcloudEvent(events = { NextcloudEvent.FileNodeCreatedEvent,
            NextcloudEvent.FileNodeDeletedEvent }, requestAuthToken = true, filter = "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\" }")
    public void onFileEvent(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        receivedFileEvents.add(event);
    }

    @OnNextcloudEvent(events = { NextcloudEvent.SystemTagAssignedEvent, NextcloudEvent.SystemTagUnassignedEvent })
    public void onSystemTagEvent(NextcloudEvent<NextcloudEvent.SystemTagEvent> event) {
        receivedSystemTagEvents.add(event);
    }

    @OnNextcloudEvent(events = {
            NextcloudEvent.FileNodeCreatedEvent }, requestAuthToken = true, filter = "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\", \"event.node.path\": \"/${nextcloud.filter.admin-uid}/files"
                    + ASYNC_STAGE_DIR + "\" }")
    public CompletableFuture<Void> onFileEventAsyncStage(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        asyncStageEvents.add(
                new PendingAsyncEvent(event, event.authentication().trigger().toUserCredentials(), completion));
        return completion;
    }

    @OnNextcloudEvent(events = {
            NextcloudEvent.FileNodeCreatedEvent }, requestAuthToken = true, filter = "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\", \"event.node.path\": \"/${nextcloud.filter.admin-uid}/files"
                    + ASYNC_UNI_DIR + "\" }")
    public Uni<Void> onFileEventAsyncUni(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        asyncUniEvents.add(
                new PendingAsyncEvent(event, event.authentication().trigger().toUserCredentials(), completion));
        return Uni.createFrom().completionStage(completion);
    }

    public List<NextcloudEvent<NextcloudEvent.FileEvent>> getReceivedFileEvents() {
        return new ArrayList<>(receivedFileEvents);
    }

    public List<NextcloudEvent<NextcloudEvent.SystemTagEvent>> getReceivedSystemTagEvents() {
        return new ArrayList<>(receivedSystemTagEvents);
    }

    public List<PendingAsyncEvent> getAsyncStageEvents() {
        return new ArrayList<>(asyncStageEvents);
    }

    public List<PendingAsyncEvent> getAsyncUniEvents() {
        return new ArrayList<>(asyncUniEvents);
    }
}
