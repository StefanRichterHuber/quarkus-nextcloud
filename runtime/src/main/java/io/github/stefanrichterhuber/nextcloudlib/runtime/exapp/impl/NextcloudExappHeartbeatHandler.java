package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudExAppStateTracker;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * Vert.x handler for the {@code /heartbeat} ExApp lifecycle endpoint.
 * Responds with a JSON {@code {"status": "ok"}} body to signal that the ExApp
 * is running and reachable by Nextcloud AppAPI.
 */
@Singleton
public class NextcloudExappHeartbeatHandler implements io.vertx.core.Handler<RoutingContext> {

    private final NextcloudExAppStateTracker stateTracker;

    @Inject
    public NextcloudExappHeartbeatHandler(NextcloudExAppStateTracker stateTracker) {
        this.stateTracker = stateTracker;
    }

    /**
     * Responds with HTTP 200 and a JSON body {@code {"status": "ok"}}.
     *
     * @param event the Vert.x routing context for the current request
     */
    @Override
    public void handle(RoutingContext event) {
        // TODO: Use quarkus healthcheck for this?
        stateTracker.heartbeat();
        event.response().putHeader("Content-Type", MediaType.APPLICATION_JSON);
        event.response().send("{\"status\": \"ok\"}");
    }

}
