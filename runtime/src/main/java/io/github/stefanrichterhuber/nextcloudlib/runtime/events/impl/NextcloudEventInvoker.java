package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.concurrent.CompletionStage;

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
     * <p>
     * Handler methods returning {@code void} are considered finished when the
     * method returns, handler methods returning a {@code CompletionStage},
     * {@code CompletableFuture} or {@code Uni} when the returned value completes.
     * Exceptions thrown synchronously by the handler method are propagated
     * directly.
     *
     * @param event the Nextcloud event to dispatch to the handler
     * @return a stage completing (normally or exceptionally) when the handler has
     *         finished its work, never {@code null} for generated invokers
     * @see NextcloudEventInvokerSupport
     */
    CompletionStage<Void> invoke(NextcloudEvent<?> event);

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
     * Returns the filter expression declared on the {@link OnNextcloudEvent}
     * annotation.
     * 
     * @return the filter expression, or an empty string if no filter was declared
     */
    default String filter() {
        return "";
    }
}
