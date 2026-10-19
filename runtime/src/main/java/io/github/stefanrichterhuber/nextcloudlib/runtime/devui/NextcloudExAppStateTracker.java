package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

/**
 * Tracks the ExApp lifecycle calls of Nextcloud for the Dev UI.
 *
 * <p>
 * In local dev mode {@link DevNextcloudExAppStateTracker} records the state. In
 * all other launch modes the {@link NoopNextcloudExAppStateTracker} is used.
 * </p>
 */
public interface NextcloudExAppStateTracker {

    /**
     * A heartbeat call was received
     */
    void heartbeat();

    /**
     * Init progress was reported to Nextcloud
     *
     * @param progress Progress 0-100
     */
    void initProgress(int progress);

    /**
     * Init failed
     *
     * @param error Error message
     */
    void initError(String error);

    /**
     * The ExApp was enabled or disabled by Nextcloud
     *
     * @param state New state
     */
    void enabled(boolean state);
}
