package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import java.util.concurrent.ScheduledExecutorService;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Produces {@link CredentialsAwareRequestScopedExecutor}s bound to the
 * credentials of the current {@link NextcloudAuthProvider}.
 * <p>
 * The producers are {@link Dependent}: each injection point receives a plain
 * executor instance (no client proxy) holding a snapshot of the credentials
 * taken at injection time. The executor may therefore be safely used from
 * other threads after the original request context has ended.
 * </p>
 * <p>
 * The executors delegate to Quarkus' {@link ScheduledExecutorService}, which
 * does not propagate the CDI request context. Every task thus runs in its own
 * fresh request context and never modifies the {@link NextcloudAuthProvider} of
 * the calling request.
 * </p>
 */
public class CredentialsAwareRequestScopedExecutorProvider {
    @Inject
    NextcloudAuthProvider authProvider;

    @Inject
    @NextcloudAdmin
    NextcloudAuthProvider adminAuthProvider;

    @Inject
    ScheduledExecutorService executor;

    /**
     * @return an executor running its tasks with the credentials of the current
     *         {@link NextcloudAuthProvider}
     */
    @Dependent
    @Produces
    CredentialsAwareRequestScopedExecutor getCredentialsAwareRequestScopedExecutor() {
        return new CredentialsAwareRequestScopedExecutor(executor, authProvider.getCredentials(), null);
    }

    /**
     * @return an executor running its tasks with the credentials of the current
     *         {@link NextcloudAdmin} {@link NextcloudAuthProvider}. The admin
     *         credentials are passed into the {@link NextcloudAdmin} qualified
     *         {@link NextcloudAuthProvider} of the task's request context
     */
    @Dependent
    @Produces
    @NextcloudAdmin
    CredentialsAwareRequestScopedExecutor getCredentialsAwareRequestScopedExecutorForAdmins() {
        return new CredentialsAwareRequestScopedExecutor(executor, null, adminAuthProvider.getCredentials());
    }
}
