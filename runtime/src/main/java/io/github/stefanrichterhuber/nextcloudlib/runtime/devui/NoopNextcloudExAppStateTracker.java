package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Reserve;

/**
 * Default {@link NextcloudExAppStateTracker} outside of local dev mode: tracks
 * nothing.
 */
@Reserve
@Priority(0)
@ApplicationScoped
public class NoopNextcloudExAppStateTracker implements NextcloudExAppStateTracker {

    @Override
    public void heartbeat() {
    }

    @Override
    public void initProgress(int progress) {
    }

    @Override
    public void initError(String error) {
    }

    @Override
    public void enabled(boolean state) {
    }
}
