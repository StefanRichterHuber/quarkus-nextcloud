package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse.StatusCode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.ext.web.RoutingContext;

/**
 * Vert.x {@link io.vertx.core.Handler} that processes incoming Nextcloud
 * webhook
 * POST requests.
 *
 * <p>
 * This handler is registered as a Vert.x route at the path configured by
 * {@link NextcloudWebhookBuildConfig#path()} (default {@code /webhook}).
 * It is instantiated by {@link NextcloudWebhookRecorder} at static-init time
 * and therefore cannot use constructor injection. All collaborators are
 * resolved lazily from the CDI container via {@link Arc#container()} on each
 * request.
 * </p>
 *
 * <h2>Request processing</h2>
 * <ol>
 * <li>Validates the shared-secret header (HTTP 401 on mismatch).</li>
 * <li>Reads the request body asynchronously.</li>
 * <li>Deserialises the JSON body into a {@link NextcloudEvent}.</li>
 * <li>Forwards the event to
 * {@link DefaultNextcloudEventDispatcher#dispatch}.</li>
 * </ol>
 */
public class NextcloudWebhookHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(NextcloudWebhookHandler.class);

    private final NextcloudWebhookBuildConfig config;
    private final NextcloudWebhookSecretHolder secretHolder;
    private final NextcloudEventDispatcher dispatcher;
    private final ObjectMapper mapper;

    public NextcloudWebhookHandler() {
        config = Arc.container().select(NextcloudWebhookBuildConfig.class)
                .get();
        secretHolder = Arc.container().select(NextcloudWebhookSecretHolder.class).get();

        dispatcher = Arc.container()
                .select(NextcloudEventDispatcher.class)
                .get();
        mapper = Arc.container().select(ObjectMapper.class).get();
    }

    /**
     * Processes an incoming webhook POST request from Nextcloud.
     *
     * <ol>
     * <li>Rejects the request with HTTP 401 when the secret header is absent or
     * does not match the configured secret (constant-time comparison).</li>
     * <li>Rejects the request with HTTP 415 when the {@code Content-Type} is not
     * {@code application/json}.</li>
     * <li>Reads the request body asynchronously (the body is not pre-populated for
     * custom Vert.x routes).</li>
     * <li>Deserialises the JSON payload into a {@link NextcloudEvent} and forwards
     * it to {@link NextcloudEventDispatcher#dispatch}.</li>
     * </ol>
     *
     * @param ctx the Vert.x routing context for the current request
     */
    @Override
    public void handle(RoutingContext ctx) {

        final String expectedSecret = secretHolder.getSecret();
        final String actualSecret = ctx.request().getHeader(config.header());

        // Runtime of the password check independent of password length!
        if (actualSecret == null || !MessageDigest.isEqual(
                actualSecret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            LOG.warn("Rejected webhook request: missing or invalid secret header");
            ctx.response().setStatusCode(StatusCode.UNAUTHORIZED).end();
            return;
        }

        // Check if content-type is json
        final String contentType = ctx.request().getHeader("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            LOG.warn("Rejected webhook request: wrong content type");
            ctx.response().setStatusCode(StatusCode.UNSUPPORTED_MEDIA_TYPE).end();
            return;
        }

        // Read body asynchronously — custom Vert.x routes don't go through the
        // Quarkus REST BodyHandler, so ctx.body() is not pre-populated.
        ctx.request().body()
                .onSuccess(buffer -> {

                    final String body = buffer.toString();
                    if (body == null || body.isBlank()) {
                        ctx.response().setStatusCode(400).end();
                        return;
                    }

                    try {
                        final NextcloudEvent<Event> event = mapper.readValue(body,
                                new TypeReference<NextcloudEvent<Event>>() {
                                });

                        if (event.authentication() != null && event.authentication().trigger() != null) {
                            final String user = event.authentication().trigger().userId();
                            final String token = event.authentication().trigger().token();
                            final String server = event.authentication().trigger().baseUrl();
                            final NextcloudUserCredentials credentials = new NextcloudUserCredentials(user, token,
                                    server);
                            dispatcher.dispatch(event, credentials);
                        } else {
                            // No authentication given, use NextcloudAuthProvider
                            final ManagedContext managedContext = Arc.container().requestContext();
                            managedContext.activate();
                            final NextcloudAuthProvider authProvider = Arc.container()
                                    .instance(NextcloudAuthProvider.class)
                                    .get();
                            try {
                                NextcloudUserCredentials credentials = authProvider.getCredentials();
                                dispatcher.dispatch(event, credentials);
                            } finally {
                                managedContext.terminate();
                            }
                        }
                        ctx.response().setStatusCode(200).end();
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to process webhook event body: %s", body);
                        ctx.response().setStatusCode(500).end();
                    }
                })
                .onFailure(err -> {
                    LOG.errorf(err, "Failed to read webhook request body");
                    ctx.response().setStatusCode(500).end();
                });

        // CRITICAL: If the request is not ended, resume it to trigger the body
        // collection
        if (!ctx.request().isEnded()) {
            ctx.request().resume();
        }
    }
}
