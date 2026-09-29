package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
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
    ScheduledExecutorService scheduledExecutorService;

    @Inject
    NextcloudWebhookRegistrationService registrationService;

    @Inject
    NextcloudWebhookConfig config;

    @Inject
    NextcloudUserService userService;

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
            // The stage returned by the invoker completes when the handler has finished
            // its (possibly asynchronous) work. Only then the temporary token may be
            // removed - regardless whether the handler succeeded or failed.
            CompletableFuture.supplyAsync(() -> invoker.invoke(event), executor)
                    .thenCompose(stage -> stage != null ? stage : CompletableFuture.<Void>completedFuture(null))
                    .whenComplete((v, err) -> {
                        if (err != null) {
                            logger.errorf(unwrap(err), "Handler '%s' failed to process event <%s>", handlerId,
                                    event);
                        }
                        cleanupTemporaryToken(credentials, executor);
                    });
        } catch (Exception e) {
            logger.errorf(e, "Failed to dispatch event <%s>", event);
        }
    }

    /**
     * Strips the {@link CompletionException} wrapper added by
     * {@link CompletableFuture} to expose the actual cause.
     */
    private static Throwable unwrap(Throwable t) {
        return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
    }

    /**
     * If requested clean up the temporary credentials created by Nextcloud. See
     * {@link NextcloudWebhookConfig#cleanupAuthTokens()} for the reason this is
     * required.
     * 
     * @param credentials Temporary credentials to delete. If this is null, the
     *                    whole method is no-op.
     * @param executor    Executor used to actually run the deletion
     */
    private void cleanupTemporaryToken(NextcloudUserCredentials credentials, Executor executor) {
        if (credentials != null && config.cleanupAuthTokens()) {
            final Runnable cleanupJob = () -> {
                executor.execute(() -> {
                    final boolean result = userService.deleteAppPassword(credentials);
                    if (result) {
                        logger.debugf("Deleted ephemeral auth token from webhook event");
                    } else {
                        logger.warnf("Failed to delete ephemeral auth token from webhook event");
                    }
                });
            };
            if (config.cleanupAuthTokenDelay().toMillis() == 0l) {
                // Immediate clean up
                cleanupJob.run();
            } else {
                // Schedule removal of credentials
                scheduledExecutorService.schedule(cleanupJob, config.cleanupAuthTokenDelay().toMillis(),
                        TimeUnit.MILLISECONDS);
            }
        }
    }

}
