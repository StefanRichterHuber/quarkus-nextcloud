package io.github.stefanrichterhuber.nextcloudlib.deployment;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileDiffService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudLoginService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.ConfiguredNextcloudAdminAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.ConfiguredNextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIAdminClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudLoginFlowRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.SardineProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.QueryIOInterceptor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class NextcloudlibProcessor {

    private static final String FEATURE = "nextcloudlib";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem additionalUnremovableBeans() {
        return AdditionalBeanBuildItem.builder() //
                .addBeanClass(SardineProvider.class)
                .setUnremovable().build();
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem indexRestClient() {
        return new AdditionalIndexedClassesBuildItem(
                NextcloudRestClient.class.getName(),
                NextcloudWebhookRestClient.class.getName(),
                QueryIOInterceptor.class.getName(),
                NextcloudLoginFlowRestClient.class.getName());
    }

    @BuildStep
    AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder() //
                .addBeanClass(NextcloudCalendarService.class)
                .addBeanClass(NextcloudContactService.class) //
                .addBeanClass(NextcloudFileService.class)
                .addBeanClass(NextcloudLoginService.class)
                .addBeanClass(NextcloudSystemTagService.class)
                .addBeanClass(ConfiguredNextcloudAdminAuthProvider.class)
                .addBeanClass(ConfiguredNextcloudAuthProvider.class)
                .addBeanClass(NextcloudAPIClientHeaders.class)
                .addBeanClass(NextcloudFileDiffService.class)
                .addBeanClass(NextcloudAPIAdminClientHeaders.class).build();
    }
}
