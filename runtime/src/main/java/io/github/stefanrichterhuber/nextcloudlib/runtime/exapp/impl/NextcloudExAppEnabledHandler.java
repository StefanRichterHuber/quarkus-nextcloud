package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.util.Objects;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppDisabledEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppEnabledEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.arc.Arc;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Event;

public class NextcloudExAppEnabledHandler implements io.vertx.core.Handler<RoutingContext> {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void handle(RoutingContext event) {
        NextcloudUserCredentials credentials = event.get(NextcloudExAppAuthHandler.PROPERT_CREDENTIALS);

        String enabled = event.request().getParam("enabled");

        Event ev = Arc.container().select(Event.class).get();
        ManagedExecutor executorService = Arc.container().select(ManagedExecutor.class).get();

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
