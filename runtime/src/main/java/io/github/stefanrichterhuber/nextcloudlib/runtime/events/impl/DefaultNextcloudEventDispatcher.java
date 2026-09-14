package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.concurrent.Executor;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.RequestScopedExecutor;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Determines the correct invoker and dispatches the event handling using the
 * {@link #scheduledExecutorService}
 * <p>
 * Annotated with {@link DefaultBean} so applications can provide their own
 * alternative implementation without needing to {@code @Specializes} this one.
 */
@ApplicationScoped
@DefaultBean
public class DefaultNextcloudEventDispatcher implements NextcloudEventDispatcher {

    @Inject
    Logger logger;

    @Inject
    ManagedExecutor scheduledExecutorService;

    @Inject
    NextcloudWebhookRegistrationService registrationService;

    @Override
    public void dispatch(String handlerId, NextcloudEvent<? extends Event> event,
            NextcloudUserCredentials credentials) {
        final boolean tokenProvided = credentials != null;
        final Executor executor = tokenProvided
                ? new CredentialsAwareRequestScopedExecutor(scheduledExecutorService,
                        credentials)
                : new RequestScopedExecutor(scheduledExecutorService);

        final NextcloudEventInvoker invoker = handlerId != null
                ? this.registrationService.getEventHandlerById(handlerId)
                : null;
        if (invoker == null) {
            logger.warnf("No NextcloudEventInvoker found for id '%s'", handlerId);
            return;
        }

        try {
            executor.execute(() -> invoker.invoke(event));
        } catch (Exception e) {
            logger.errorf(e, "Failed to dispatch event <%s>", event);
        }
    }
}
