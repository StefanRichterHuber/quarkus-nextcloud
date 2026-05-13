package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.quarkus.arc.Arc;
import io.vertx.ext.web.RoutingContext;

/**
 * Vert.x {@link io.vertx.core.Handler} that processes incoming Nextcloud
 * webhook
 * POST requests.
 *
 * <p>
 * This handler is registered as a Vert.x route at the path configured by
 * {@link NextcloudWebhookBuildConfig#path()} (default {@code /webhook}).
 * It is instantiated by {@link NextcloudEventRecorder} at static-init time
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
 * <li>Forwards the event to {@link NextcloudEventDispatcher#dispatch}.</li>
 * </ol>
 */
public class NextcloudWebhookHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(NextcloudWebhookHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        NextcloudWebhookBuildConfig config = Arc.container().select(NextcloudWebhookBuildConfig.class).get();
        NextcloudWebhookSecretHolder secretHolder = Arc.container().select(NextcloudWebhookSecretHolder.class).get();

        final String expectedSecret = secretHolder.getSecret();
        final String actualSecret = ctx.request().getHeader(config.header());

        // Runtime of the password check indepentend of password length!
        if (!MessageDigest.isEqual(
                actualSecret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            LOG.warn("Rejected webhook request: missing or invalid secret header");
            ctx.response().setStatusCode(401).end();
            return;
        }

        // Check if content-type is json
        final String contentType = ctx.request().getHeader("Content-Type");
        if (!Objects.equals("application/json", contentType)) {
            LOG.warn("Rejected webhook request: wrong content type");
            ctx.response().setStatusCode(401).end();
            return;
        }

        // Read body asynchronously — custom Vert.x routes don't go through the
        // Quarkus REST BodyHandler, so ctx.body() is not pre-populated.
        ctx.request().body()
                .onSuccess(buffer -> {
                    NextcloudEventDispatcher dispatcher = Arc.container().select(NextcloudEventDispatcher.class).get();
                    ObjectMapper mapper = Arc.container().select(ObjectMapper.class).get();

                    String body = buffer.toString();
                    if (body == null || body.isBlank()) {
                        ctx.response().setStatusCode(400).end();
                        return;
                    }

                    try {
                        NextcloudEvent<Event> event = mapper.readValue(body,
                                new TypeReference<NextcloudEvent<Event>>() {
                                });
                        dispatcher.dispatch(event);
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
