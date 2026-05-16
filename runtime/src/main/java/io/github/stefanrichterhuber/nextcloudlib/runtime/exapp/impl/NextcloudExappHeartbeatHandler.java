package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.MediaType;

public class NextcloudExappHeartbeatHandler implements io.vertx.core.Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext event) {
        // TODO: Use quarkus healthcheck for this?
        event.response().putHeader("Content-Type", MediaType.APPLICATION_JSON);
        event.response().send("{\"status\": \"ok\"}");
    }

}
