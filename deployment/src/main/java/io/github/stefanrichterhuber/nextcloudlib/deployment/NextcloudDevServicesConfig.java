package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "nextcloud.dev-services")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface NextcloudDevServicesConfig {

    /**
     * Nextcloud image to use
     * 
     * @return
     */
    @WithDefault("nextcloud:latest")
    String image();

    /**
     * Default nextcloud user to set up
     * 
     * @return
     */
    @WithDefault("admin")
    String user();

    /**
     * Password of the default nextcloud user
     * 
     * @return
     */
    Optional<String> password();

    /**
     * Log level: 0 Debug, 1 Info, 2 Warning, 3 Error, 4 Fatal
     * 
     * @return
     */
    @WithDefault("0")
    int logLevel();

    /**
     * List of nextcloud apps to install
     * 
     * @return
     */
    Optional<List<String>> apps();

    /**
     * Enable ex app suppor in the dev service
     * 
     * @return
     */
    @WithDefault("false")
    boolean enableExApp();

    /**
     * Enable the webhook worker job to ensure events are passed down asap
     * 
     * @return
     */
    @WithDefault("false")
    boolean enableWebhookWorker();
}
