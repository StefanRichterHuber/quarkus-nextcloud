package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.List;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;

/**
 * Build-time-generated invoker for a single {@link OnNextcloudEvent}-annotated
 * method.
 *
 * <p>
 * One implementation class is generated per handler method by
 * {@link io.github.stefanrichterhuber.nextcloudlib.deployment.NextcloudEventProcessor}
 * using Gizmo. The generated class casts the {@code bean} to the declaring CDI
 * bean type
 * and calls the handler method directly — no reflection at request time.
 * </p>
 */
public interface NextcloudEventInvoker {
    void invoke(NextcloudEvent<?> event);

    String[] events();
}
