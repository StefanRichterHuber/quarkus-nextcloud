package io.github.stefanrichterhuber.nextcloudlib.deployment;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppAuthHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppEnabledHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppRouteConfigurer;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExappHeartbeatHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExappInitHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth.ExAppNextcloudAdminAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth.ExAppNextcloudAuthProvider;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.RouteHandler;

/**
 * Quarkus build-time processor that wires the Nextcloud ExApp lifecycle
 * infrastructure. Active only when {@link IsExApp} evaluates to {@code true}.
 *
 * <p>Registers the three mandatory ExApp lifecycle endpoints ({@code /heartbeat},
 * {@code /init}, {@code /enabled}) as Vert.x routes and contributes the ExApp
 * auth-provider beans to the CDI container.
 */
class NextcloudExAppProcessor {
    private static final Logger LOG = Logger.getLogger(NextcloudExAppProcessor.class);

    private static final String SERVICE_PREFIX = "io.github.stefanrichterhuber.nextcloudlib.exapp.";
    /** Service name of the {@code /heartbeat} route handler */
    private static final String HEARTBEAT_HANDLER_SERVICE = SERVICE_PREFIX + "heartbeat-handler";
    /** Service name of the {@code /init} route configurer */
    private static final String INIT_ROUTE_SERVICE = SERVICE_PREFIX + "init-route";
    /** Service name of the {@code /enabled} route configurer */
    private static final String ENABLED_ROUTE_SERVICE = SERVICE_PREFIX + "enabled-route";

    /**
     * Registers the ExApp lifecycle Vert.x routes ({@code /heartbeat}, {@code /init},
     * {@code /enabled}) at runtime. The routes are only produced when ExApp support
     * is enabled. {@code /init} and {@code /enabled} validate the AppAPI request
     * headers ({@link NextcloudExAppAuthHandler}) before the actual handler runs.
     *
     * <p>The handlers are CDI beans (see {@link #registerExAppBeans}), resolved at
     * application start by runtime services. Their lambdas are copied into the
     * application, so they may only reference runtime classes.
     *
     * @param services    registrar for the runtime services that create the route
     *                    handlers
     * @param buildConfig ExApp configuration providing the endpoint paths
     * @param httpRoot    Quarkus HTTP root path for route registration
     * @param routes      producer for Vert.x route build items
     */
    @BuildStep(onlyIf = IsExApp.class)
    // RouteBuildItem only accepts recorder proxies. ServiceRegistrar#getRecorderProxy is the
    // (deprecated) bridge for that until vertx-http offers service based routes; Quarkus
    // itself uses it the same way (HttpSecurityProcessor).
    @SuppressWarnings("deprecation")
    // The handler beans are resolved from the container, so run after it was set up
    @Consume(BeanContainerBuildItem.class)
    void registerExAppRoutes(
            ServiceRegistrar services,
            NextcloudExappConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            BuildProducer<RouteBuildItem> routes) {

        LOG.infof("Registering Nextcloud ExApp lifecycle Vert.x route at: /heartbeat");

        services.forService(RouteHandler.class, HEARTBEAT_HANDLER_SERVICE)
                .require(ArcContainer.class)
                .onStart((ctx, arc) -> arc.instance(NextcloudExappHeartbeatHandler.class).get()::handle);

        routes.produce(httpRoot.routeBuilder()
                .route("/heartbeat")
                .handler(services.getRecorderProxy(RouteHandler.class, HEARTBEAT_HANDLER_SERVICE))
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        LOG.infof("Registering Nextcloud ExApp lifecycle Vert.x route at: /init");

        services.forService(NextcloudExAppRouteConfigurer.class, INIT_ROUTE_SERVICE)
                .require(ArcContainer.class)
                .onStart((ctx, arc) -> {
                    final NextcloudExAppAuthHandler auth = arc.instance(NextcloudExAppAuthHandler.class).get();
                    final NextcloudExappInitHandler handler = arc.instance(NextcloudExappInitHandler.class).get();
                    return route -> {
                        route.handler(auth);
                        route.handler(handler);
                    };
                });

        routes.produce(httpRoot.routeBuilder()
                .routeFunction("/init",
                        services.getRecorderProxy(NextcloudExAppRouteConfigurer.class, INIT_ROUTE_SERVICE))
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        LOG.infof("Registering Nextcloud ExApp lifecycle Vert.x route at: /enabled");

        services.forService(NextcloudExAppRouteConfigurer.class, ENABLED_ROUTE_SERVICE)
                .require(ArcContainer.class)
                .onStart((ctx, arc) -> {
                    final NextcloudExAppAuthHandler auth = arc.instance(NextcloudExAppAuthHandler.class).get();
                    final NextcloudExAppEnabledHandler handler = arc.instance(NextcloudExAppEnabledHandler.class).get();
                    return route -> {
                        route.handler(auth);
                        route.handler(handler);
                    };
                });

        routes.produce(httpRoot.routeBuilder()
                .routeFunction("/enabled",
                        services.getRecorderProxy(NextcloudExAppRouteConfigurer.class, ENABLED_ROUTE_SERVICE))
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());
    }

    /**
     * Registers the ExApp-specific auth-provider CDI beans
     * ({@link ExAppNextcloudAdminAuthProvider} and {@link ExAppNextcloudAuthProvider})
     * and the lifecycle route handlers when ExApp support is enabled.
     *
     * @param additionalBeans producer for CDI bean registrations
     */
    @BuildStep(onlyIf = IsExApp.class)
    void registerExAppBeans(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(ExAppNextcloudAdminAuthProvider.class)
                .addBeanClass(ExAppNextcloudAuthProvider.class)
                .addBeanClass(NextcloudExAppAuthHandler.class)
                .addBeanClass(NextcloudExappHeartbeatHandler.class)
                .addBeanClass(NextcloudExappInitHandler.class)
                .addBeanClass(NextcloudExAppEnabledHandler.class)
                .setUnremovable()
                .build());

    }
}
