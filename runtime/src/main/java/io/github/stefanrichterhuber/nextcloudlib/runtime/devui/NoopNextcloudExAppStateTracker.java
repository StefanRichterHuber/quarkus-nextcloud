package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default {@link NextcloudExAppStateTracker} outside of local dev mode: tracks
 * nothing.
 */
@DefaultBean
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
