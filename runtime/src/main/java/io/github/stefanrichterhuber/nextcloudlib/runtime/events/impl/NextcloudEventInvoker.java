package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;

/**
 * Build-time-generated invoker for a single {@link OnNextcloudEvent}-annotated
 * method.
 *
 * One implementation class is generated per handler
 * using Gizmo. The generated code uses CDI to look-up the target bean and then
 * directly invokes the target method, without runtime reflection.
 */
public interface NextcloudEventInvoker {
    /**
     * Invokes the {@link OnNextcloudEvent}-annotated handler method with the
     * given event.
     *
     * @param event the Nextcloud event to dispatch to the handler
     */
    void invoke(NextcloudEvent<?> event);

    /**
     * Returns the fully-qualified Nextcloud PHP event class names this invoker
     * is subscribed to.
     *
     * @return array of Nextcloud event class names, never {@code null} or empty
     */
    String[] events();

    /**
     * Whether a temporary auth token must be requested from Nextcloud for the
     * triggering user before the handler is invoked.
     *
     * @return {@code true} when a temporary auth token is required
     */
    boolean requestAuthToken();

    /**
     * Whether a {@link io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider}
     * instance carrying the triggering user's token should be placed in the
     * request context before the handler is called.
     *
     * @return {@code true} when an {@code NextcloudAuthProvider} should be provided
     */
    boolean provideAuthProvider();
}
