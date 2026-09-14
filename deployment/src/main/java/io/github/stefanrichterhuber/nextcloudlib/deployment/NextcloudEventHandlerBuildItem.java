package io.github.stefanrichterhuber.nextcloudlib.deployment;

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
    private final String[] eventClassNames;
    private final boolean requestAuthToken;

    private final String filter;

    /**
     * @param declaringClassName fully-qualified name of the CDI bean class that
     *                           declares the handler
     * @param methodName         name of the
     *                           {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated
     *                           method
     * @param eventClassNames    fully-qualified Nextcloud PHP event class names the
     *                           method listens for
     * @param requestAuthToken   {@code true} when a temporary auth token must be
     *                           requeded from Nextcloud
     */
    public NextcloudEventHandlerBuildItem(String declaringClassName, String methodName, String[] eventClassNames,
            boolean requestAuthToken, String filter) {
        this.declaringClassName = declaringClassName;
        this.methodName = methodName;
        this.eventClassNames = eventClassNames;
        this.requestAuthToken = requestAuthToken;
        this.filter = filter;
    }

    /**
     * Fully-qualified name of the CDI bean class that declares the handler method.
     */
    public String getDeclaringClassName() {
        return declaringClassName;
    }

    /** Name of the annotated handler method. */
    public String getMethodName() {
        return methodName;
    }

    /** Fully-qualified Nextcloud PHP event class names the method listens for. */
    public String[] getEventClassNames() {
        return eventClassNames;
    }

    /**
     * @return {@code true} when a temporary Nextcloud auth token must be requested
     *         for the
     *         triggering user before dispatching the event
     */
    public boolean isRequestAuthToken() {
        return requestAuthToken;
    }

    /**
     * @return the filter for the event handler
     */
    public String getFilter() {
        return filter;
    }
}
