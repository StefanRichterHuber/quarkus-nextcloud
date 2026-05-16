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

    public boolean isTokenNeeded() {
        return tokenNeeded;
    }

    public boolean isProvideAuth() {
        return provideAuth;
    }
}
