package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.List;

/**
 * Carries the build-time-discovered metadata for a single {@link OnNextcloudEvent}
 * handler method. Passed from the deployment processor to the runtime recorder
 * so the dispatcher can locate and invoke the bean method without class-scanning
 * or reflection at request time.
 *
 * @param declaringClassName fully-qualified name of the CDI bean class
 * @param invokerClassName   fully-qualified name of the Gizmo-generated {@link NextcloudEventInvoker}
 * @param eventClassNames    fully-qualified Nextcloud PHP event class names this handler listens for
 */
public record HandlerDescriptor(String declaringClassName, String invokerClassName, List<String> eventClassNames) {
}
