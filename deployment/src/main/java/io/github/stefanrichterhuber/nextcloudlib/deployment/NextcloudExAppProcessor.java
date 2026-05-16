package io.github.stefanrichterhuber.nextcloudlib.deployment;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppAuthHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppEnabledHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExappHeartbeatHandler;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExappInitHandler;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

public class NextcloudExAppProcessor {
    private static final Logger LOG = Logger.getLogger(NextcloudExAppProcessor.class);

    @BuildStep(onlyIf = IsExApp.class)
    void registerWebhookRoute(
            NextcloudExappConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            BuildProducer<RouteBuildItem> routes) {

        routes.produce(httpRoot.routeBuilder()
                .route("/heartbeat")
                .handler(new NextcloudExappHeartbeatHandler())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        routes.produce(httpRoot.routeBuilder()
                .route("/init")
                .handler(new NextcloudExAppAuthHandler())
                .handler(new NextcloudExappInitHandler())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        routes.produce(httpRoot.routeBuilder()
                .route("/enabled")
                .handler(new NextcloudExAppAuthHandler())
                .handler(new NextcloudExAppEnabledHandler())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());
    }
}
