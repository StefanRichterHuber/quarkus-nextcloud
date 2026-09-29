package io.github.stefanrichterhuber.nextcloudlib.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Carries build-time-discovered metadata for a single
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated
 * method. One instance is produced per annotated method by
 * {@link NextcloudEventProcessor}.
 */
public final class NextcloudEventHandlerBuildItem extends MultiBuildItem {

    /**
     * Supported kinds of return types of an
     * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated
     * method.
     */
    public enum ReturnKind {
        /** Method returns {@code void} */
        VOID,
        /**
         * Method returns a {@link java.util.concurrent.CompletionStage} or
         * {@link java.util.concurrent.CompletableFuture}
         */
        STAGE,
        /** Method returns a {@code io.smallrye.mutiny.Uni} */
        UNI
    }

    private final String declaringClassName;
    private final String methodName;
    private final String[] eventClassNames;
    private final boolean requestAuthToken;

    private final String filter;

    private final String returnTypeName;
    private final ReturnKind returnKind;

    /**
     * @param declaringClassName fully-qualified name of the CDI bean class that
     *                           declares the handler
     * @param methodName         name of the
     *                           {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated
     *                           method
     * @param eventClassNames    fully-qualified Nextcloud PHP event class names the
     *                           method listens for
     * @param requestAuthToken   {@code true} when a temporary auth token must be
     *                           requested from Nextcloud
     * @param filter             filter expression declared on the annotation
     * @param returnTypeName     fully-qualified (erased) name of the method's
     *                           return type, e.g. {@code void} or
     *                           {@code java.util.concurrent.CompletableFuture}
     * @param returnKind         kind of the method's return type
     */
    public NextcloudEventHandlerBuildItem(String declaringClassName, String methodName, String[] eventClassNames,
            boolean requestAuthToken, String filter, String returnTypeName, ReturnKind returnKind) {
        this.declaringClassName = declaringClassName;
        this.methodName = methodName;
        this.eventClassNames = eventClassNames;
        this.requestAuthToken = requestAuthToken;
        this.filter = filter;
        this.returnTypeName = returnTypeName;
        this.returnKind = returnKind;
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

    /**
     * @return fully-qualified (erased) name of the handler method's return type
     */
    public String getReturnTypeName() {
        return returnTypeName;
    }

    /**
     * @return kind of the handler method's return type
     */
    public ReturnKind getReturnKind() {
        return returnKind;
    }
}
