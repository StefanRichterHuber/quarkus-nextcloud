package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.Objects;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.quarkus.arc.Arc;
import io.vertx.ext.web.RoutingContext;

/**
 * Vert.x {@link io.vertx.core.Handler} that processes incoming Nextcloud webhook
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
 *   <li>Validates the shared-secret header (HTTP 401 on mismatch).</li>
 *   <li>Reads the request body asynchronously.</li>
 *   <li>Deserialises the JSON body into a {@link NextcloudEvent}.</li>
 *   <li>Forwards the event to {@link NextcloudEventDispatcher#dispatch}.</li>
 * </ol>
 */
public class NextcloudWebhookHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(NextcloudWebhookHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        NextcloudWebhookBuildConfig config = Arc.container().select(NextcloudWebhookBuildConfig.class).get();
        NextcloudWebhookSecretHolder secretHolder = Arc.container().select(NextcloudWebhookSecretHolder.class).get();

        String expected = secretHolder.getSecret();
        String actual = ctx.request().getHeader(config.header());

        if (!Objects.equals(expected, actual)) {
            LOG.warn("Rejected webhook request: missing or invalid secret header");
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
    }
}
