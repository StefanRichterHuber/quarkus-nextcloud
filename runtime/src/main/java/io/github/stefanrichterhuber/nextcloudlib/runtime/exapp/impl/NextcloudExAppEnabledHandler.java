package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.util.Objects;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.RestResponse.StatusCode;

import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudExAppStateTracker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppDisabledEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppEnabledEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Vert.x handler for the {@code /enabled} ExApp lifecycle endpoint. Called by
 * Nextcloud AppAPI when the ExApp is enabled or disabled. Fires the
 * corresponding CDI {@link ExAppEnabledEvent} or {@link ExAppDisabledEvent}
 * under a request context scoped to the triggering user's credentials.
 */
@Singleton
public class NextcloudExAppEnabledHandler implements io.vertx.core.Handler<RoutingContext> {

    private final ManagedExecutor executorService;
    private final Event<Object> ev;
    private final NextcloudExAppStateTracker stateTracker;

    @Inject
    public NextcloudExAppEnabledHandler(ManagedExecutor executorService, Event<Object> ev,
            NextcloudExAppStateTracker stateTracker) {
        this.executorService = executorService;
        this.ev = ev;
        this.stateTracker = stateTracker;
    }

    /**
     * Reads the {@code enabled} query parameter and fires either
     * {@link ExAppEnabledEvent} ({@code enabled=1}) or
     * {@link ExAppDisabledEvent} ({@code enabled=0}) on the CDI event bus.
     * Responds with HTTP 401 when credentials are missing from the routing
     * context (i.e. the auth handler did not run).
     *
     * @param event the Vert.x routing context for the current request
     */
    @Override
    public void handle(RoutingContext event) {
        final NextcloudUserCredentials credentials = event.get(NextcloudExAppAuthHandler.PROPERTY_CREDENTIALS);
        if (credentials == null) {
            event.response().setStatusCode(StatusCode.UNAUTHORIZED).end();
            return;
        }

        final String enabled = event.request().getParam("enabled");
        stateTracker.enabled(Objects.equals(enabled, "1"));

        new CredentialsAwareRequestScopedExecutor(executorService, credentials).execute(() -> {
            if (Objects.equals(enabled, "1")) {
                ev.fire(new ExAppEnabledEvent());
            } else {
                ev.fire(new ExAppDisabledEvent());
            }
        });

        event.response().putHeader("Content-Type", jakarta.ws.rs.core.MediaType.APPLICATION_JSON);
        event.response().end("{\"error\": \"\"}");
    }

}
