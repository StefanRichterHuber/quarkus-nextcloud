package io.github.stefanrichterhuber.nextcloudlib.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestEventHandlers {
    private final List<NextcloudEvent<NextcloudEvent.FileEvent>> receivedFileEvents = new CopyOnWriteArrayList<>();
    private final List<NextcloudEvent<NextcloudEvent.SystemTagEvent>> receivedSystemTagEvents = new CopyOnWriteArrayList<>();

    @OnNextcloudEvent(events = { NextcloudEvent.FileNodeCreatedEvent,
            NextcloudEvent.FileNodeDeletedEvent }, filter = "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\" }")
    public void onFileEvent(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        receivedFileEvents.add(event);
    }

    @OnNextcloudEvent(events = { NextcloudEvent.SystemTagAssignedEvent, NextcloudEvent.SystemTagUnassignedEvent })
    public void onSystemTagEvent(NextcloudEvent<NextcloudEvent.SystemTagEvent> event) {
        receivedSystemTagEvents.add(event);
    }

    public List<NextcloudEvent<NextcloudEvent.FileEvent>> getReceivedFileEvents() {
        return new ArrayList<>(receivedFileEvents);
    }

    public List<NextcloudEvent<NextcloudEvent.SystemTagEvent>> getReceivedSystemTagEvents() {
        return new ArrayList<>(receivedSystemTagEvents);
    }
}