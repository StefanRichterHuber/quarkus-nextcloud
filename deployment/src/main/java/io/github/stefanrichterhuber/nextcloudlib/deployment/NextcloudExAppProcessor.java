package io.github.stefanrichterhuber.nextcloudlib.deployment;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppRecorder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth.ExAppNextcloudAdminAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth.ExAppNextcloudAuthProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

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

    /**
     * Registers the ExApp lifecycle Vert.x routes ({@code /heartbeat}, {@code /init},
     * {@code /enabled}) at runtime. The routes are only produced when ExApp support
     * is enabled.
     *
     * @param recorder    runtime recorder that creates the route handlers
     * @param buildConfig ExApp configuration providing the endpoint paths
     * @param httpRoot    Quarkus HTTP root path for route registration
     * @param routes      producer for Vert.x route build items
     */
    @BuildStep(onlyIf = IsExApp.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerExAppRoutes(
            NextcloudExAppRecorder recorder,
            NextcloudExappConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            BuildProducer<RouteBuildItem> routes) {

        LOG.infof("Registering Nextcloud ExApp lifecycle Vert.x route at: /heartbeat");

        routes.produce(httpRoot.routeBuilder()
                .route("/heartbeat")
                .handler(recorder.heartBeatHandler())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        LOG.infof("Registering Nextcloud ExApp lifecycle Vert.x route at: /init");

        routes.produce(httpRoot.routeBuilder()
                .routeFunction("/init", recorder.initRoute())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        LOG.infof("Registering Nextcloud ExApp lifecycle Vert.x route at: /enabled");

        routes.produce(httpRoot.routeBuilder()
                .routeFunction("/enabled", recorder.enabledRoute())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());
    }

    /**
     * Registers the ExApp-specific auth-provider CDI beans
     * ({@link ExAppNextcloudAdminAuthProvider} and {@link ExAppNextcloudAuthProvider})
     * when ExApp support is enabled.
     *
     * @param additionalBeans producer for CDI bean registrations
     */
    @BuildStep(onlyIf = IsExApp.class)
    void registerExAppBeans(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(ExAppNextcloudAdminAuthProvider.class)
                .addBeanClass(ExAppNextcloudAuthProvider.class)
                .setUnremovable()
                .build());

    }
}
