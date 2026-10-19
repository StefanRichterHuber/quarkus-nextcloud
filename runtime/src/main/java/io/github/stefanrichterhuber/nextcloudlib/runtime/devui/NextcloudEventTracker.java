package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;

/**
 * Tracks received Nextcloud webhook events for the Dev UI event inspector.
 *
 * <p>
 * In local dev mode {@link DevNextcloudEventTracker} records the events. In all
 * other launch modes the {@link NoopNextcloudEventTracker} is used, so
 * production code can call the tracker unconditionally.
 * </p>
 */
public interface NextcloudEventTracker {

    /**
     * Processing status of a captured event
     */
    enum Status {
        /** Handler is still running */
        PENDING,
        /** Handler finished successfully */
        OK,
        /** Event could not be parsed or the handler failed */
        FAILED,
        /** No handler registered for the handler id of the webhook */
        NO_HANDLER,
        /** A custom dispatcher is used, the outcome is unknown */
        UNTRACKED
    }

    /**
     * Credentials used to dispatch an event
     */
    enum CredentialSource {
        /** Ephemeral token sent by Nextcloud with the webhook */
        ORIGINAL,
        /**
         * Temporary app password of the user of the original event, created for a
         * replay because the original token was already deleted. Deleted again after
         * the replay.
         */
        TEMPORARY,
        /** No user context */
        NONE
    }

    /**
     * Captures a received event
     *
     * @param handlerId        Id of the handler the webhook was sent to
     * @param rawJson          Raw JSON body of the event
     * @param eventClass       Nextcloud PHP event class, if known
     * @param credentials      Credentials sent with the event, may be null
     * @param replayOf         Id of the event replayed, null for original events
     * @param credentialSource Credentials used to dispatch the event
     * @return Id of the captured event or {@code null} if events are not tracked
     */
    String capture(String handlerId, String rawJson, String eventClass, NextcloudUserCredentials credentials,
            String replayOf, CredentialSource credentialSource);

    /**
     * Marks the processing of an event as finished
     *
     * @param id    Id of the event, no-op if null
     * @param error Error thrown by the handler, null on success
     */
    void complete(String id, Throwable error);

    /**
     * Sets the status of an event
     *
     * @param id     Id of the event, no-op if null
     * @param status New status
     * @param error  Optional error message
     */
    void setStatus(String id, Status status, String error);

    /**
     * Marks the ephemeral token of an event as deleted in Nextcloud
     *
     * @param id Id of the event, no-op if null
     */
    void markTokenRevoked(String id);
}
