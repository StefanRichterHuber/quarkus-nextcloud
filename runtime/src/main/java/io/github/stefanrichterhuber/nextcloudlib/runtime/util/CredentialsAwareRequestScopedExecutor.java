package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import java.util.Objects;
import java.util.concurrent.Executor;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

/**
 * Creates a new executor wrapping an existing one but starting
 * a request context in the new thread and pass the given credentials into the
 * {@link NextcloudAuthProvider} of this new request context
 * 
 */
public final class CredentialsAwareRequestScopedExecutor implements Executor {

    private final Executor delegate;
    private final NextcloudUserCredentials credentials;

    public CredentialsAwareRequestScopedExecutor(Executor delegate, NextcloudUserCredentials credentials) {
        this.delegate = Objects.requireNonNull(delegate);
        this.credentials = Objects.requireNonNull(credentials);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> {
            final ManagedContext ctx = Arc.container().requestContext();
            final boolean contextWasActive = ctx.isActive();

            if (!contextWasActive) {
                ctx.activate();
            }

            try (var handle = Arc.container().instance(NextcloudAuthProvider.class)) {
                final NextcloudAuthProvider authProvider = handle.get();
                final NextcloudUserCredentials previous = contextWasActive ? authProvider.getCredentials() : null;
                try {
                    authProvider.setCredentials(credentials);
                    command.run();
                } finally {
                    if (contextWasActive) {
                        authProvider.setCredentials(previous);
                    }
                }
            } finally {
                if (!contextWasActive) {
                    ctx.deactivate();
                    ctx.terminate();
                }
            }
        });
    }
}
