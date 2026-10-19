package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Records webhook events. The payloads are deserialized with Jackson into the
 * polymorphic {@link NextcloudEvent.Event} sub types and dispatched by the
 * generated invokers.
 */
@ApplicationScoped
public class EventRecorder {
    private final List<NextcloudEvent<NextcloudEvent.FileEvent>> fileEvents = new CopyOnWriteArrayList<>();
    private final List<NextcloudEvent<NextcloudEvent.SystemTagEvent>> systemTagEvents = new CopyOnWriteArrayList<>();
    private final List<NextcloudEvent<NextcloudEvent.CalendarObjectEvent>> calendarEvents = new CopyOnWriteArrayList<>();

    @OnNextcloudEvent(events = { NextcloudEvent.FileNodeCreatedEvent,
            NextcloudEvent.FileNodeDeletedEvent }, requestAuthToken = true, filter = "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\" }")
    public void onFileEvent(NextcloudEvent<NextcloudEvent.FileEvent> event) {
        fileEvents.add(event);
    }

    @OnNextcloudEvent(events = { NextcloudEvent.SystemTagAssignedEvent, NextcloudEvent.SystemTagUnassignedEvent })
    public void onSystemTagEvent(NextcloudEvent<NextcloudEvent.SystemTagEvent> event) {
        systemTagEvents.add(event);
    }

    @OnNextcloudEvent(events = { NextcloudEvent.CalendarObjectCreatedEvent })
    public void onCalendarEvent(NextcloudEvent<NextcloudEvent.CalendarObjectEvent> event) {
        calendarEvents.add(event);
    }

    public NextcloudEvent<NextcloudEvent.FileEvent> awaitFileEvent(String className,
            Predicate<NextcloudEvent<NextcloudEvent.FileEvent>> predicate) throws InterruptedException {
        return await(fileEvents, className, predicate);
    }

    public NextcloudEvent<NextcloudEvent.SystemTagEvent> awaitSystemTagEvent(String className,
            Predicate<NextcloudEvent<NextcloudEvent.SystemTagEvent>> predicate) throws InterruptedException {
        return await(systemTagEvents, className, predicate);
    }

    public NextcloudEvent<NextcloudEvent.CalendarObjectEvent> awaitCalendarEvent(String className,
            Predicate<NextcloudEvent<NextcloudEvent.CalendarObjectEvent>> predicate) throws InterruptedException {
        return await(calendarEvents, className, predicate);
    }

    /**
     * Waits up to 20 seconds for a matching event (below the 30 seconds HTTP timeout of the tests)
     *
     * @return the event or null if none was received
     */
    private static <T extends NextcloudEvent.Event> NextcloudEvent<T> await(List<NextcloudEvent<T>> events,
            String className, Predicate<NextcloudEvent<T>> predicate) throws InterruptedException {
        for (int rounds = 20; rounds > 0; rounds--) {
            for (NextcloudEvent<T> event : events) {
                if (event.event().className().equals(className) && predicate.test(event)) {
                    return event;
                }
            }
            Thread.sleep(1000);
        }
        return null;
    }
}
