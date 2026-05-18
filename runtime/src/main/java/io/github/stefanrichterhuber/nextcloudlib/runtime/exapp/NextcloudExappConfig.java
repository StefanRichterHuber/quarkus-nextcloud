package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Nextcloud ExApp feature ({@code nextcloud.exapp.*}).
 * Uses {@link ConfigPhase#BUILD_AND_RUN_TIME_FIXED} so the value is available
 * at both build time (to gate {@code @BuildStep} conditions) and at runtime.
 */
@ConfigMapping(prefix = "nextcloud.exapp")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface NextcloudExappConfig {
    /**
     * Enables ExApp mode, turning this Quarkus application into a Nextcloud
     * External Application (ExApp) managed by the AppAPI.
     *
     * @return {@code true} when ExApp mode is active, defaults to {@code false}
     */
    @WithDefault("false")
    boolean enabled();

}
