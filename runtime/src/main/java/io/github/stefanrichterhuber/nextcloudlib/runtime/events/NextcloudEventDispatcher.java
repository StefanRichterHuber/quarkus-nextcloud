package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.RequestScopedExecutor;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;

/**
 * Dispatches an incoming {@link NextcloudEvent} to the registered (by its
 * invokerId)
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudEventInvoker}
 * instance
 * <p>
 * Since this dispatcher is called in the hot event loop of vertx, no expensive
 * operations are allowed. Just delegate to another thread using
 * {@link RequestScopedExecutor} or
 * {@link CredentialsAwareRequestScopedExecutor}
 * </p>
 *
 * <p>
 * The default implementation is
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.DefaultNextcloudEventDispatcher}.
 * Applications may provide an alternative {@code @DefaultBean} to override it.
 */
public interface NextcloudEventDispatcher {
    /**
     * Dispatches one event to the invoker with the given id
     *
     * @param invokerId   ID of the invoker (derived from the path of the webhook
     *                    event)
     * @param event       the event received from Nextcloud
     * @param credentials credentials identifying the user that triggered the event.
     *                    Might be null, if nextcloud provided no credentials!
     */
    void dispatch(String invokerId, NextcloudEvent<? extends Event> event, NextcloudUserCredentials credentials);
}
