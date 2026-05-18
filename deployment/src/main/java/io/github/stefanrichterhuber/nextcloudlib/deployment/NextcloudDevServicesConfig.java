package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for the Nextcloud dev service
 * ({@code nextcloud.dev-services.*} properties).
 */
@ConfigMapping(prefix = "nextcloud.dev-services")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface NextcloudDevServicesConfig {

    /**
     * Docker image to use for the Nextcloud container.
     *
     * @return image name, defaults to {@code nextcloud:latest}
     */
    @WithDefault("nextcloud:latest")
    String image();

    /**
     * Username of the Nextcloud admin account created during first-time setup.
     *
     * @return admin username, defaults to {@code admin}
     */
    @WithDefault("admin")
    String user();

    /**
     * Password for the Nextcloud admin account. When absent, a random
     * alphanumeric password is generated at startup.
     *
     * @return admin password, or empty to auto-generate
     */
    Optional<String> password();

    /**
     * Nextcloud log verbosity level.
     * {@code 0} = Debug, {@code 1} = Info, {@code 2} = Warning,
     * {@code 3} = Error, {@code 4} = Fatal.
     *
     * @return log level, defaults to {@code 1} (Info)
     */
    @WithDefault("1")
    int logLevel();

    /**
     * Additional Nextcloud apps to install and enable on startup.
     *
     * @return list of app identifiers, or empty if none
     */
    Optional<List<String>> apps();

    /**
     * Whether to install and configure the {@code app_api} Nextcloud app so that
     * ExApp (external application) support is available in the dev service.
     *
     * @return {@code true} to enable ExApp support, defaults to {@code false}
     */
    @WithDefault("false")
    boolean enableExApp();

    /**
     * Whether to run the {@code WebhookCall} background-job worker periodically so
     * that webhook events are dispatched without relying on a cron trigger.
     *
     * @return {@code true} to run the worker, defaults to {@code true}
     */
    @WithDefault("true")
    boolean enableWebhookWorker();
}
