package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.function.BooleanSupplier;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;

/**
 * {@link BooleanSupplier} used in {@code @BuildStep(onlyIf = IsExApp.class)} conditions
 * to gate build steps on whether Nextcloud ExApp support is enabled via configuration.
 */
public class IsExApp implements BooleanSupplier {
    NextcloudExappConfig config;

    /**
     * @return {@code true} when ExApp support is enabled in the configuration
     */
    public boolean getAsBoolean() {
        return config.enabled();
    }
}
