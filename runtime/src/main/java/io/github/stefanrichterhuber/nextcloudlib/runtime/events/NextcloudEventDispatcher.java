package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;

/**
 * Dispatches an incoming {@link NextcloudEvent} to all registered
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudEventInvoker}
 * instances whose declared event list includes the event's class name.
 *
 * <p>The default implementation is
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.DefaultNextcloudEventDispatcher}.
 * Applications may provide an alternative {@code @DefaultBean} to override it.
 */
public interface NextcloudEventDispatcher {
    /**
     * Dispatches one event to all suitable invokers, executing each handler under
     * the user identity described by the given credentials.
     *
     * @param event       the event received from Nextcloud
     * @param credentials credentials identifying the user that triggered the event
     */
    void dispatch(NextcloudEvent<? extends Event> event, NextcloudUserCredentials credentials);
}
