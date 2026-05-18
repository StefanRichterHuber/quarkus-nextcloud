package io.github.stefanrichterhuber.nextcloudlib.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Captures Nextcloud webhook events received during integration tests.
 *
 * <p>
 * Tests poll {@link #poll(long, TimeUnit)} after triggering a Nextcloud action
 * to assert that the expected event arrived within a reasonable timeout.
 * </p>
 */
@ApplicationScoped
public class WebhookEventCaptor {

    private final LinkedBlockingQueue<NextcloudEvent<?>> queue = new LinkedBlockingQueue<>();

    @OnNextcloudEvent(events = {
            NextcloudEvent.FileNodeCreatedEvent,
            NextcloudEvent.FileNodeDeletedEvent,
            NextcloudEvent.FileNodeWrittenEvent
    }, provideAuth = true, tokenNeeded = true)
    public void onFileEvent(NextcloudEvent<?> event) {
        queue.add(event);
    }

    /**
     * Waits up to {@code timeout} for the next event to arrive.
     *
     * @return the event, or {@code null} if the timeout elapsed
     */
    public NextcloudEvent<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public List<NextcloudEvent<?>> receivedEvents() {
        List<NextcloudEvent<?>> result = new ArrayList<>();
        queue.drainTo(result);
        return result;
    }

    /**
     * Discards all queued events — call before each test that checks event
     * delivery.
     */
    public void reset() {
        queue.clear();
    }
}
