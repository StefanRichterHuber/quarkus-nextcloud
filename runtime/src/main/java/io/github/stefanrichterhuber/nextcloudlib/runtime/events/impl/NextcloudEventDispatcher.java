package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.Objects;
import java.util.concurrent.Executor;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.RequestScopedExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Injects all instances of NextcloudEventInvoker and and dispatches one event
 * to all suitable invokers
 */
@ApplicationScoped
public class NextcloudEventDispatcher {

    @Inject
    Instance<NextcloudEventInvoker> invokers;

    @Inject
    Logger logger;

    @Inject
    ManagedExecutor scheduledExecutorService;

    void dispatch(NextcloudEvent<Event> event) {
        final String eventClass = event.event().className();
        for (NextcloudEventInvoker invoker : invokers) {
            for (String invokerEvent : invoker.events()) {
                if (Objects.equals(eventClass, invokerEvent)) {

                    try {
                        Executor executor = null;
                        if (invoker.provideAuthProvider()) {
                            if (event.authentication() != null && event.authentication().trigger() != null) {
                                final String user = event.authentication().trigger().userId();
                                final String token = event.authentication().trigger().token();
                                final String server = event.authentication().trigger().baseUrl();
                                NextcloudUserCredentials creds = new NextcloudUserCredentials(user, token, server);

                                executor = new CredentialsAwareRequestScopedExecutor(scheduledExecutorService, creds);
                            } else {
                                logger.error(
                                        "Requested temporary token for events from nextcloud, but none received. Fall back to otherwise provided credentials");
                                executor = new RequestScopedExecutor(scheduledExecutorService);
                            }
                        } else {
                            executor = new RequestScopedExecutor(scheduledExecutorService);
                        }
                        if (executor != null) {
                            executor.execute(() -> invoker.invoke(event));
                        }
                        break;
                    } catch (Exception e) {
                        logger.errorf(e, "Failed to dispatch event <%s>", event);
                    }
                }
            }
        }
    }
}
