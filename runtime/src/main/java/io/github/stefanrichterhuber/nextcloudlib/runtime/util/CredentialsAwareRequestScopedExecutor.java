package io.github.stefanrichterhuber.nextcloudlib.runtime.util;

import java.util.Objects;
import java.util.concurrent.Executor;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.ManagedContext;

/**
 * Creates a new executor wrapping an existing one but starting a request
 * context in the new thread (if none is active yet) and passing the given
 * credentials into the {@link NextcloudAuthProvider}s of this request context:
 * <ul>
 * <li>user credentials into the default {@link NextcloudAuthProvider}</li>
 * <li>admin credentials into the {@link NextcloudAdmin} qualified
 * {@link NextcloudAuthProvider}</li>
 * </ul>
 * Credentials given as {@code null} are not passed, the corresponding
 * {@link NextcloudAuthProvider} keeps its own credentials. If a request context
 * was already active, the previous credentials are restored after the task.
 */
public final class CredentialsAwareRequestScopedExecutor implements Executor {

    private final Executor delegate;
    private final NextcloudUserCredentials userCredentials;
    private final NextcloudUserCredentials adminCredentials;

    /**
     * Creates a new CredentialsAwareRequestScopedExecutor and propagates the given
     * user credentials into the execution context
     *
     * @param delegate        Executor to actually execute the code
     * @param userCredentials User level credentials to pass (null value is allowed,
     *                        no new credentials will be passed)
     */
    public CredentialsAwareRequestScopedExecutor(Executor delegate, NextcloudUserCredentials userCredentials) {
        this(delegate, userCredentials, null);
    }

    /**
     * Creates a new CredentialsAwareRequestScopedExecutor and propagates the given
     * user and admin credentials into the execution context
     *
     * @param delegate         Executor to actually execute the code
     * @param userCredentials  User-level credentials to pass (null value is
     *                         allowed, no new credentials will be passed)
     * @param adminCredentials Admin-level credentials to pass (null value is
     *                         allowed, no new credentials will be passed)
     */
    public CredentialsAwareRequestScopedExecutor(Executor delegate, NextcloudUserCredentials userCredentials,
            NextcloudUserCredentials adminCredentials) {
        if (delegate instanceof CredentialsAwareRequestScopedExecutor ce) {
            // Avoid deep nesting of CredentialsAwareRequestScopedExecutor. Credentials not
            // given are taken from the inner executor.
            delegate = ce.delegate;
            adminCredentials = adminCredentials != null ? adminCredentials : ce.adminCredentials;
            userCredentials = userCredentials != null ? userCredentials : ce.userCredentials;
        }
        this.delegate = Objects.requireNonNull(delegate);
        this.userCredentials = userCredentials;
        this.adminCredentials = adminCredentials;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> {
            final ManagedContext ctx = Arc.container().requestContext();
            final boolean contextWasActive = ctx.isActive();

            if (!contextWasActive) {
                ctx.activate();
            }

            try (InstanceHandle<NextcloudAuthProvider> userAuthHandle = Arc.container()
                    .instance(NextcloudAuthProvider.class);
                    InstanceHandle<NextcloudAuthProvider> adminAuthHandle = Arc.container()
                            .select(NextcloudAuthProvider.class, NextcloudAdmin.Literal.instance())
                            .getHandle()) {
                final NextcloudAuthProvider userAuthProvider = userCredentials != null ? userAuthHandle.get() : null;
                final NextcloudAuthProvider adminAuthProvider = adminCredentials != null ? adminAuthHandle.get()
                        : null;

                // Previous credentials are only required (and only read) if they must be
                // restored in an already active request context. Reading them in a fresh
                // context could fail, e.g. if no credentials are configured.
                final NextcloudUserCredentials prevUserCreds = contextWasActive && userAuthProvider != null
                        ? userAuthProvider.getCredentials()
                        : null;
                final NextcloudUserCredentials prevAdminCreds = contextWasActive && adminAuthProvider != null
                        ? adminAuthProvider.getCredentials()
                        : null;
                try {
                    if (userAuthProvider != null) {
                        userAuthProvider.setCredentials(userCredentials);
                    }
                    if (adminAuthProvider != null) {
                        adminAuthProvider.setCredentials(adminCredentials);
                    }
                    command.run();
                } finally {
                    if (contextWasActive) {
                        if (userAuthProvider != null) {
                            userAuthProvider.setCredentials(prevUserCreds);
                        }
                        if (adminAuthProvider != null) {
                            adminAuthProvider.setCredentials(prevAdminCreds);
                        }
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
