package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration of ext apps
 */
@ConfigMapping(prefix = "nextcloud.exapp")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface NextcloudExappConfig {
    /**
     * Enable this feature, makes this an Nextcloud Ex APP
     * 
     * @return
     */
    @WithDefault("false")
    boolean enabled();

}
