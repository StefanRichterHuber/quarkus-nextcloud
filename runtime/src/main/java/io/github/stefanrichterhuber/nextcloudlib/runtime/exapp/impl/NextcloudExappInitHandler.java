package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.net.URI;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse.StatusCode;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppApiRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppApiRestClient.AppInitProgress;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudInitStateProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.MediaType;

public class NextcloudExappInitHandler implements io.vertx.core.Handler<RoutingContext> {
    private static final Logger LOG = Logger.getLogger(NextcloudExappInitHandler.class);

    /**
     * Creates a new instance of {@link ExAppApiRestClient} with the configured
     * server URL and authentication headers
     * 
     * @return Instance of {@link ExAppApiRestClient}
     */
    private ExAppApiRestClient getClient() {
        final NextcloudAuthProvider authProvider = Arc.container().select(NextcloudAuthProvider.class).get();
        final ExAppApiRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(ExAppApiRestClient.class);
        return client;
    }

    @Override
    public void handle(RoutingContext event) {
        // If the application does not need to carry out long initialization, it has an
        // option to not implement “/init” endpoint, so AppAPI will get 404 or 501 error
        // on it’s request, but you can consider that initialization to be done and this
        // section can be skipped.
        final InjectableInstance<NextcloudInitStateProvider> stateProviderInstance = Arc.container()
                .select(NextcloudInitStateProvider.class);
        if (!stateProviderInstance.isResolvable()) {
            LOG.debugf("No NextcloudInitStateProvider provided, short ex app init process");
            event.response().setStatusCode(StatusCode.NOT_FOUND).end();
            return;
        }

        final NextcloudInitStateProvider stateProvider = stateProviderInstance.get();
        final ExAppApiRestClient client = getClient();

        // There is an NextcloudInitStateProvider, subscribe to the provided state
        stateProvider.getInitProgressReporter().subscribe().with(
                item -> {
                    client.reportAppInitProgress(AppInitProgress.ok(item));
                },
                t -> {
                    client.reportAppInitProgress(AppInitProgress.error(0, t.getMessage()));
                });

        event.response().putHeader("Content-Type", MediaType.APPLICATION_JSON);
        event.response().end("{}");
    }

}
