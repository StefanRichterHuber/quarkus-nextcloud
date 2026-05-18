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
    private final boolean tokenNeeded;

    private final boolean provideAuth;

    /**
     * @param declaringClassName fully-qualified name of the CDI bean class that declares the handler
     * @param methodName         name of the {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated method
     * @param eventClassNames    fully-qualified Nextcloud PHP event class names the method listens for
     * @param tokenNeeded        {@code true} when a temporary auth token must be fetched from Nextcloud
     * @param provideAuth        {@code true} when a {@code NextcloudAuthProvider} should be placed in the request context
     */
    public NextcloudEventHandlerBuildItem(String declaringClassName, String methodName, String[] eventClassNames,
            boolean tokenNeeded, boolean provideAuth) {
        this.declaringClassName = declaringClassName;
        this.methodName = methodName;
        this.eventClassNames = eventClassNames;
        this.tokenNeeded = tokenNeeded;
        this.provideAuth = provideAuth;
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
     * @return {@code true} when a temporary Nextcloud auth token must be requested for the
     *         triggering user before dispatching the event
     */
    public boolean isTokenNeeded() {
        return tokenNeeded;
    }

    /**
     * @return {@code true} when a {@code NextcloudAuthProvider} carrying the triggering
     *         user's token should be placed in the request context before the handler is called
     */
    public boolean isProvideAuth() {
        return provideAuth;
    }
}
