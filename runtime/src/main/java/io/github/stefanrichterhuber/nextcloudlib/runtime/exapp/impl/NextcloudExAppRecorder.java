package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.util.function.Consumer;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

/**
 * Quarkus {@link Recorder} that creates the Vert.x handlers and route
 * configurers for the Nextcloud ExApp lifecycle endpoints ({@code /heartbeat},
 * {@code /init}, {@code /enabled}).
 */
@Recorder
public class NextcloudExAppRecorder {

    /**
     * Creates the handler for the {@code /init} endpoint.
     *
     * @return a new {@link NextcloudExappInitHandler} instance
     */
    public Handler<RoutingContext> initHandler() {
        return new NextcloudExappInitHandler();
    }

    /**
     * Creates the handler for the {@code /heartbeat} endpoint.
     *
     * @return a new {@link NextcloudExappHeartbeatHandler} instance
     */
    public Handler<RoutingContext> heartBeatHandler() {
        return new NextcloudExappHeartbeatHandler();
    }

    /**
     * Creates the handler for the {@code /enabled} endpoint.
     *
     * @return a new {@link NextcloudExAppEnabledHandler} instance
     */
    public Handler<RoutingContext> enabledHandler() {
        return new NextcloudExAppEnabledHandler();
    }

    /**
     * Creates the authentication handler that validates AppAPI request headers.
     *
     * @return a new {@link NextcloudExAppAuthHandler} instance
     */
    public Handler<RoutingContext> authHandler() {
        return new NextcloudExAppAuthHandler();
    }

    /**
     * Returns a route configurer for {@code /init} that chains the auth handler
     * before the init handler.
     *
     * @return a {@link Consumer} that configures the {@code /init} route
     */
    public Consumer<Route> initRoute() {
        return route -> {
            route.handler(authHandler());
            route.handler(initHandler());
        };
    }

    /**
     * Returns a route configurer for {@code /enabled} that chains the auth handler
     * before the enabled/disabled handler.
     *
     * @return a {@link Consumer} that configures the {@code /enabled} route
     */
    public Consumer<Route> enabledRoute() {
        return route -> {
            route.handler(authHandler());
            route.handler(enabledHandler());
        };
    }

}
