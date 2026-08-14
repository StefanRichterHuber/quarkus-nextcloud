package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for the Nextcloud webhook integration.
 * 
 * 
 */
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "nextcloud.webhook.build")
public interface NextcloudWebhookBuildConfig {

    /** Path at which the Nextcloud webhook endpoint is mounted. */
    @WithDefault("/webhook")
    String path();

    /**
     * If true, automatic discovery of webhook event handlers at build-time is
     * enabled
     * 
     * @return
     */
    @WithDefault("true")
    boolean autoDiscoveryEnabled();

}
