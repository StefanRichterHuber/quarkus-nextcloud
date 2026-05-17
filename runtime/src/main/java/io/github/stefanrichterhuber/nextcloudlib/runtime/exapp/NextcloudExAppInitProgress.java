package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import io.smallrye.mutiny.Multi;

public interface NextcloudExAppInitProgress {

    /**
     * Report the status of the initialization
     */
    Multi<Integer> getInitProgressReporter();
}
