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

    @OnNextcloudEvent(events = { NextcloudEvent.FileNodeCreatedEvent, NextcloudEvent.FileNodeDeletedEvent })
    public void onEvent(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        receivedFileEvents.add(event);
    }

    public List<NextcloudEvent<NextcloudEvent.FileEvent>> getReceivedFileEvents() {
        return new ArrayList<>(receivedFileEvents);
    }
}