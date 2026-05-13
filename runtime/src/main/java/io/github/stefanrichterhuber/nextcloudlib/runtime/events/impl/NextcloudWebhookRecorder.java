package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class NextcloudWebhookRecorder {

    public Handler<RoutingContext> webhookHandler() {
        return new NextcloudWebhookHandler();
    }
}