package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.List;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Quarkus recorder that wires build-time-discovered metadata into runtime objects.
 *
 * <p>
 * Called during {@code STATIC_INIT} by the deployment processor. Two objects are
 * produced:
 * </p>
 * <ul>
 *   <li>A {@link NextcloudEventDispatcher} pre-loaded with all handler descriptors
 *       collected at build time, exposed as a synthetic CDI {@code @Singleton}
 *       bean.</li>
 *   <li>A Vert.x {@link Handler}{@code <RoutingContext>} that receives incoming
 *       webhook POST requests and delegates to the dispatcher.</li>
 * </ul>
 */
@Recorder
public class NextcloudEventRecorder {

    /**
     * Creates the event dispatcher pre-loaded with all handler descriptors
     * collected at build time.
     *
     * @param descriptors one entry per {@link OnNextcloudEvent}-annotated method
     * @return a {@link RuntimeValue} wrapping the ready-to-use dispatcher
     */
    public RuntimeValue<NextcloudEventDispatcher> createDispatcher(List<HandlerDescriptor> descriptors) {
        return new RuntimeValue<>(new NextcloudEventDispatcher(descriptors));
    }

    /**
     * Creates the Vert.x route handler that receives Nextcloud webhook POST
     * requests. CDI lookups are deferred to request time, so this method may be
     * called at {@code STATIC_INIT} before the CDI container is fully started.
     *
     * @return a handler suitable for use with {@link io.quarkus.vertx.http.deployment.RouteBuildItem}
     */
    public Handler<RoutingContext> createWebhookHandler() {
        return new NextcloudWebhookHandler();
    }
}
