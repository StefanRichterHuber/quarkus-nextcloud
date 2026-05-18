package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.Objects;
import java.util.concurrent.Executor;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Default {@link NextcloudEventDispatcher} implementation that fans out an
 * incoming event to every {@link NextcloudEventInvoker} whose declared event
 * list contains the event's class name.
 *
 * <p>Each matching invoker is executed on a {@link ManagedExecutor} thread
 * wrapped in a {@code CredentialsAwareRequestScopedExecutor} so that the CDI
 * request context is properly activated and the triggering user's credentials
 * are available inside the handler.
 *
 * <p>Annotated with {@link DefaultBean} so applications can provide their own
 * alternative implementation without needing to {@code @Specializes} this one.
 */
@ApplicationScoped
@DefaultBean
public class DefaultNextcloudEventDispatcher implements NextcloudEventDispatcher {

    @Inject
    Instance<NextcloudEventInvoker> invokers;

    @Inject
    Logger logger;

    @Inject
    ManagedExecutor scheduledExecutorService;

    /**
     * Iterates all known {@link NextcloudEventInvoker} instances and, for each one
     * that is registered for the event's class name, submits the invocation to the
     * managed executor. Errors thrown by individual invokers are caught and logged
     * so that a failing handler does not prevent other handlers from running.
     *
     * @param event       the event received from Nextcloud
     * @param credentials credentials identifying the user that triggered the event
     */
    public void dispatch(NextcloudEvent<? extends Event> event, NextcloudUserCredentials credentials) {
        final Executor executor = new CredentialsAwareRequestScopedExecutor(scheduledExecutorService,
                credentials);
        final String eventClass = event.event().className();
        for (NextcloudEventInvoker invoker : invokers) {
            for (String invokerEvent : invoker.events()) {
                if (Objects.equals(eventClass, invokerEvent)) {
                    try {
                        executor.execute(() -> invoker.invoke(event));
                        break;
                    } catch (Exception e) {
                        logger.errorf(e, "Failed to dispatch event <%s>", event);
                    }
                }
            }
        }
    }
}
