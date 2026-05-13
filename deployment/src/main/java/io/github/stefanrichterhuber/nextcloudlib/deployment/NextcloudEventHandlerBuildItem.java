package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Carries build-time-discovered metadata for a single
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated
 * method. One instance is produced per annotated method by
 * {@link NextcloudEventProcessor}.
 */
public final class NextcloudEventHandlerBuildItem extends MultiBuildItem {

    private final String declaringClassName;
    private final String methodName;
    private final List<String> eventClassNames;

    public NextcloudEventHandlerBuildItem(String declaringClassName, String methodName, List<String> eventClassNames) {
        this.declaringClassName = declaringClassName;
        this.methodName = methodName;
        this.eventClassNames = eventClassNames;
    }

    /** Fully-qualified name of the CDI bean class that declares the handler method. */
    public String getDeclaringClassName() {
        return declaringClassName;
    }

    /** Name of the annotated handler method. */
    public String getMethodName() {
        return methodName;
    }

    /** Fully-qualified Nextcloud PHP event class names the method listens for. */
    public List<String> getEventClassNames() {
        return eventClassNames;
    }
}
