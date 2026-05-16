package io.github.stefanrichterhuber.nextcloudlib.deployment;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.NextcloudExAppRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

class NextcloudExAppProcessor {
    private static final Logger LOG = Logger.getLogger(NextcloudExAppProcessor.class);

    @BuildStep(onlyIf = IsExApp.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerWebhookRoute(
            NextcloudExAppRecorder recorder,
            NextcloudExappConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            BuildProducer<RouteBuildItem> routes) {

        routes.produce(httpRoot.routeBuilder()
                .route("/heartbeat")
                .handler(recorder.heartBeatHandler())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        routes.produce(httpRoot.routeBuilder()
                .routeFunction("/init", recorder.initRoute())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());

        routes.produce(httpRoot.routeBuilder()
                .routeFunction("/enabled", recorder.enabledRoute())
                .displayOnNotFoundPage("Nextcloud ExApp")
                .build());
    }
}
