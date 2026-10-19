package io.github.stefanrichterhuber.nextcloudlib.test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;

/**
 * Event handler used by {@link NextcloudDevUILocalDevServiceTest}
 */
public class DevUITestEventHandler {

    @OnNextcloudEvent(events = { NextcloudEvent.FileNodeCreatedEvent }, requestAuthToken = true)
    public void onFileCreated(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        // Fails for paths containing "fail" to test the error reporting
        if (event.event().node() != null && event.event().node().path().contains("fail")) {
            throw new IllegalStateException("Handler failure for " + event.event().node().path());
        }
    }
}
