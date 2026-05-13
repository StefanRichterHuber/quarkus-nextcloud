package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import java.util.concurrent.Executor;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

/**
 * Creates a new exeutor wrapping an existing one but starting
 * a request context in the new thread and pass the given credentials into the
 * NextcloudLocalAuthProvider of this new request context
 * 
 */
public final class CredentialsAwareRequestScopedExecutor implements Executor {

    private final Executor delegate;
    private final NextcloudUserCredentials credentials;

    public CredentialsAwareRequestScopedExecutor(Executor delegate, NextcloudUserCredentials credentials) {
        this.delegate = delegate;
        this.credentials = credentials;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> {
            final ManagedContext ctx = Arc.container().requestContext();
            ctx.activate();
            final NextcloudAuthProvider authProvider = Arc.container().instance(NextcloudAuthProvider.class)
                    .get();
            try {
                authProvider.setCredentials(credentials);
                command.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                ctx.terminate();
            }
        });
    }
}
