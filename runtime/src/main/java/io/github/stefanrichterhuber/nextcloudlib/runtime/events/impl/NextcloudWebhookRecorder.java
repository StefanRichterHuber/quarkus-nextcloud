package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Quarkus {@link Recorder} that creates the {@link NextcloudWebhookHandler}
 * instance used as a Vert.x route handler. The recorder runs at
 * {@code RUNTIME_INIT} time so the handler is available when the route is
 * registered.
 */
@Recorder
public class NextcloudWebhookRecorder {

    /**
     * Creates a new {@link NextcloudWebhookHandler} to be registered as a Vert.x
     * route handler for the Nextcloud webhook endpoint.
     *
     * @return a new handler instance
     */
    public Handler<RoutingContext> webhookHandler() {
        return new NextcloudWebhookHandler();
    }
}