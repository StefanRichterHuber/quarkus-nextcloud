package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;

/**
 * Build-time-generated invoker for a single {@link OnNextcloudEvent}-annotated
 * method.
 *
 * <p>
 * One implementation class is generated per handler method by
 * {@link io.github.stefanrichterhuber.nextcloudlib.deployment.NextcloudEventProcessor}
 * using Gizmo. The generated code uses CDI to look-up the target bean and then
 * directly invokes the target method, without runtime reflection.
 */
public interface NextcloudEventInvoker {
    /**
     * Invokes method annotated with OnNextcloudEvent
     * 
     * @param event
     */
    void invoke(NextcloudEvent<?> event);

    /**
     * List of nextcloud events bound to the annotated methods
     * 
     * @return
     */
    String[] events();

    /**
     * Request the auth token for the triggering user?
     * 
     * @return
     */
    boolean requestAuthToken();

    /**
     * Whether to provide an Authprovider instance with the auth token
     * 
     * @return
     */
    boolean provideAuthProvider();
}
