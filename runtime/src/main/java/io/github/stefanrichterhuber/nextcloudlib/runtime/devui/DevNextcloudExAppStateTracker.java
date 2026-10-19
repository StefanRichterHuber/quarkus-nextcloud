package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * {@link NextcloudExAppStateTracker} of local dev mode: keeps the last observed
 * ExApp lifecycle state for the Dev UI. The state is static, so it survives hot
 * reloads like the captured events. Only registered in local dev mode,
 * overriding the {@link NoopNextcloudExAppStateTracker}.
 */
@ApplicationScoped
public class DevNextcloudExAppStateTracker implements NextcloudExAppStateTracker {

    /**
     * Immutable view of the ExApp state
     *
     * @param lastHeartbeat     Epoch millis of the last heartbeat call, null if
     *                          none
     * @param heartbeatCount    Number of heartbeat calls received
     * @param initProgress      Last reported init progress (0-100), null if none
     * @param initError         Last init error message, null if none
     * @param enabled           Last enabled state reported by Nextcloud, null if
     *                          unknown
     * @param lastEnabledChange Epoch millis of the last enabled/disabled call
     */
    public record Snapshot(Long lastHeartbeat, long heartbeatCount, Integer initProgress, String initError,
            Boolean enabled, Long lastEnabledChange) {
    }

    private static volatile Long lastHeartbeat;
    private static volatile long heartbeatCount;
    private static volatile Integer lastInitProgress;
    private static volatile String lastInitError;
    private static volatile Boolean lastEnabled;
    private static volatile Long lastEnabledChange;

    @Override
    public void heartbeat() {
        lastHeartbeat = System.currentTimeMillis();
        heartbeatCount++;
    }

    @Override
    public void initProgress(int progress) {
        lastInitProgress = progress;
        lastInitError = null;
    }

    @Override
    public void initError(String error) {
        lastInitError = error;
    }

    @Override
    public void enabled(boolean state) {
        lastEnabled = state;
        lastEnabledChange = System.currentTimeMillis();
    }

    /**
     * @return Last observed ExApp state
     */
    Snapshot snapshot() {
        return new Snapshot(lastHeartbeat, heartbeatCount, lastInitProgress, lastInitError, lastEnabled,
                lastEnabledChange);
    }
}
