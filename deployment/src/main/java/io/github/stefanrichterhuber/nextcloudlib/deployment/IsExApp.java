package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.function.BooleanSupplier;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;

public class IsExApp implements BooleanSupplier {
    NextcloudExappConfig config;

    public boolean getAsBoolean() {
        return config.enabled();
    }
}
