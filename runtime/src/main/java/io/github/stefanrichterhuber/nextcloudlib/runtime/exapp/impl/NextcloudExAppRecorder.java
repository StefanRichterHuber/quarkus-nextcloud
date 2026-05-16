package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.util.function.Consumer;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class NextcloudExAppRecorder {

    public Handler<RoutingContext> initHandler() {
        return new NextcloudExappInitHandler();
    }

    public Handler<RoutingContext> heartBeatHandler() {
        return new NextcloudExappHeartbeatHandler();
    }

    public Handler<RoutingContext> enabledHandler() {
        return new NextcloudExAppEnabledHandler();
    }

    public Handler<RoutingContext> authHandler() {
        return new NextcloudExAppAuthHandler();
    }

    public Consumer<Route> initRoute() {
        return route -> {
            route.handler(authHandler());
            route.handler(initHandler());
        };
    }

    public Consumer<Route> enabledRoute() {
        return route -> {
            route.handler(authHandler());
            route.handler(enabledHandler());
        };
    }
}
