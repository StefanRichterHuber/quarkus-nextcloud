package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse.StatusCode;

import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudExAppStateTracker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppApiRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppApiRestClient.AppInitProgress;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExAppInitProgress;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * Vert.x handler for the {@code /init} ExApp lifecycle endpoint. Called by
 * Nextcloud AppAPI after enabling the ExApp to allow it to perform
 * long-running initialization before going live.
 *
 * <p>If no {@link NextcloudExAppInitProgress} bean is present, responds with
 * HTTP 404 — AppAPI interprets this as "no initialization needed". When a
 * progress bean is found, its reactive stream is subscribed and each emitted
 * percentage value is forwarded to the AppAPI's {@code ex-app/status} endpoint.
 */
@Singleton
public class NextcloudExappInitHandler implements io.vertx.core.Handler<RoutingContext> {
    private static final Logger LOG = Logger.getLogger(NextcloudExappInitHandler.class);

    private final NextcloudExAppStateTracker stateTracker;
    /** Request scoped, resolved on each call by its client proxy */
    private final NextcloudAuthProvider authProvider;
    /** Optional, provided by the application */
    private final Instance<NextcloudExAppInitProgress> stateProviderInstance;
    private final ScheduledExecutorService executorService;

    @Inject
    public NextcloudExappInitHandler(NextcloudExAppStateTracker stateTracker, NextcloudAuthProvider authProvider,
            Instance<NextcloudExAppInitProgress> stateProviderInstance, ScheduledExecutorService executorService) {
        this.stateTracker = stateTracker;
        this.authProvider = authProvider;
        this.stateProviderInstance = stateProviderInstance;
        this.executorService = executorService;
    }

    /**
     * Creates a new instance of {@link ExAppApiRestClient} with the configured
     * server URL and authentication headers
     * 
     * @return Instance of {@link ExAppApiRestClient}
     */
    private ExAppApiRestClient getClient() {
        final ExAppApiRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(ExAppApiRestClient.class);
        return client;
    }

    /**
     * Handles the AppAPI {@code /init} request. Returns HTTP 404 immediately when
     * no {@link NextcloudExAppInitProgress} bean is resolvable. Otherwise
     * subscribes to the progress stream and forwards each value to the AppAPI's
     * status endpoint asynchronously, then responds with HTTP 200 and an empty
     * JSON body.
     *
     * @param event the Vert.x routing context for the current request
     */
    @Override
    public void handle(RoutingContext event) {
        // If the application does not need to carry out long initialization, it has an
        // option to not implement “/init” endpoint, so AppAPI will get 404 or 501 error
        // on it’s request, but you can consider that initialization to be done and this
        // section can be skipped.
        if (!stateProviderInstance.isResolvable()) {
            LOG.debugf("No NextcloudInitStateProvider provided, short ex app init process");
            event.response().setStatusCode(StatusCode.NOT_FOUND).end();
            return;
        }

        final NextcloudExAppInitProgress stateProvider = stateProviderInstance.get();
        final ExAppApiRestClient client = getClient();
        final NextcloudUserCredentials credentials = event.get(NextcloudExAppAuthHandler.PROPERTY_CREDENTIALS);
        final Executor exec = new CredentialsAwareRequestScopedExecutor(executorService, credentials);

        // There is an NextcloudInitStateProvider, subscribe to the provided state
        stateProvider.getInitProgressReporter().subscribe().with(
                item -> {
                    final int realItem;
                    if (item < 0 || item > 100) {
                        LOG.warnf(
                                "Status init progress reported is out of bounds, should be 0<=x<=100, is %d. Will be truncated to valid value",
                                item);
                        if (item < 0) {
                            realItem = 0;
                        } else if (item > 100) {
                            realItem = 100;
                        } else {
                            realItem = item;
                        }
                    } else {
                        realItem = item;
                    }
                    stateTracker.initProgress(realItem);
                    exec.execute(() -> client.reportAppInitProgress(AppInitProgress.ok(realItem)));
                },
                t -> {
                    stateTracker.initError(t.getMessage());
                    exec.execute(() -> client.reportAppInitProgress(AppInitProgress.error(0, t.getMessage())));
                    ;
                });

        event.response().putHeader("Content-Type", MediaType.APPLICATION_JSON);
        event.response().end("{}");
    }

}
