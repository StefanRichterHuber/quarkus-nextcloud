package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import java.util.Objects;
import java.util.concurrent.Executor;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

/**
 * Creates a new executor wrapping an existing one, but starting a request
 * context in the new thread
 */
public class RequestScopedExecutor implements Executor {
    private final Executor delegate;

    public RequestScopedExecutor(final Executor delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> {
            final ManagedContext ctx = Arc.container().requestContext();
            ctx.activate();
            try {
                command.run();
            } finally {
                ctx.terminate();
            }
        });
    }
}
