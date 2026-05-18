package io.github.stefanrichterhuber.nextcloudlib.deployment;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCommentService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileDiffService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudLoginService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.ConfiguredNextcloudAdminAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.ConfiguredNextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIAdminClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudLoginFlowRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudWebhookRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.QueryIOInterceptor;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.SardineProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.ExAppApiRestClient;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Core Quarkus build-time processor for the {@code nextcloudlib} extension.
 * Declares the extension feature flag, ensures all required CDI beans are
 * registered, and adds REST client interfaces to the Jandex index so that
 * Quarkus can discover them.
 */
class NextcloudlibProcessor {

    private static final String FEATURE = "nextcloudlib";

    /**
     * Declares the {@code nextcloudlib} feature so it appears in the Quarkus
     * banner on startup.
     *
     * @return the feature build item
     */
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Registers {@link SardineProvider} as an unremovable CDI bean so that ArC
     * does not prune it during unused-bean detection.
     *
     * @return the additional-bean build item
     */
    @BuildStep
    AdditionalBeanBuildItem additionalUnremovableBeans() {
        return AdditionalBeanBuildItem.builder() //
                .addBeanClass(SardineProvider.class)
                .setUnremovable().build();
    }

    /**
     * Adds the Nextcloud REST client interfaces to the Jandex index so that the
     * MicroProfile REST Client extension can discover and process them even when
     * they are not in the application's own module.
     *
     * @return the additional-indexed-classes build item
     */
    @BuildStep
    AdditionalIndexedClassesBuildItem indexRestClient() {
        return new AdditionalIndexedClassesBuildItem(
                NextcloudRestClient.class.getName(),
                NextcloudWebhookRestClient.class.getName(),
                ExAppApiRestClient.class.getName(),
                QueryIOInterceptor.class.getName(),
                NextcloudLoginFlowRestClient.class.getName());
    }

    /**
     * Registers the core Nextcloud service and auth-provider CDI beans.
     *
     * @return the additional-bean build item
     */
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
                .addBeanClass(NextcloudUserService.class)
                .addBeanClass(NextcloudCommentService.class)
                .addBeanClass(NextcloudAPIAdminClientHeaders.class).build();
    }
}
