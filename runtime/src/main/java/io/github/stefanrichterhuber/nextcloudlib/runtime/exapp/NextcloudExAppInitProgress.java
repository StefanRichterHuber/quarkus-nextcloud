package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import io.smallrye.mutiny.Multi;

/**
 * Implemented by application beans that need to perform long-running
 * initialization and want to report progress to Nextcloud's AppAPI.
 *
 * <p>When a CDI bean implementing this interface is present, the ExApp
 * {@code /init} endpoint subscribes to the stream it returns and forwards each
 * value to the AppAPI's {@code ex-app/status} endpoint. A missing bean causes
 * the {@code /init} handler to respond with HTTP 404, which AppAPI interprets
 * as "no initialization needed".
 */
public interface NextcloudExAppInitProgress {

    /**
     * Returns a reactive stream that emits initialization progress values.
     * Each emitted integer must be in the range {@code [0, 100]}, where
     * {@code 100} signals completion. Values outside this range are clamped.
     *
     * @return a {@link Multi} emitting progress percentages from 0 to 100
     */
    Multi<Integer> getInitProgressReporter();
}
