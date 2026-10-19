package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudEventTracker.CredentialSource;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudEventTracker.Status;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;

/**
 * Static ring buffer behind {@link DevNextcloudEventTracker}.
 *
 * <p>
 * The storage is static on purpose: CDI beans are recreated on every hot
 * reload, but extension runtime classes are loaded by the base runtime class
 * loader, which survives it. So the captured events survive a hot reload. The
 * raw JSON of each event is stored, not the deserialized object, so no
 * application classes are retained across reloads.
 * </p>
 */
final class NextcloudDevEventStore {

    /**
     * Immutable view of a captured event, as sent to the Dev UI. Contains no
     * secrets.
     */
    public record EventSnapshot(String id, long timestamp, String handlerId, String eventClass, String triggerUser,
            String rawJson, Status status, String error, Long durationMs, boolean tokenRevoked, String replayOf,
            CredentialSource credentialSource) {
    }

    /**
     * Mutable entry of the store. Only accessible within this package, since it
     * holds the credentials of the event.
     */
    static final class Entry {
        final String id;
        final long timestamp;
        final String handlerId;
        final String eventClass;
        final String rawJson;
        final NextcloudUserCredentials credentials;
        final String replayOf;
        final CredentialSource credentialSource;
        volatile Status status = Status.PENDING;
        volatile String error;
        volatile Long durationMs;
        volatile boolean tokenRevoked;

        Entry(String id, String handlerId, String eventClass, String rawJson, NextcloudUserCredentials credentials,
                String replayOf, CredentialSource credentialSource) {
            this.id = id;
            this.timestamp = System.currentTimeMillis();
            this.handlerId = handlerId;
            this.eventClass = eventClass;
            this.rawJson = rawJson;
            this.credentials = credentials;
            this.replayOf = replayOf;
            this.credentialSource = credentialSource;
        }

        EventSnapshot snapshot() {
            return new EventSnapshot(id, timestamp, handlerId, eventClass,
                    credentials != null ? credentials.loginName() : null, rawJson, status, error, durationMs,
                    tokenRevoked, replayOf, credentialSource);
        }
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final List<Consumer<EventSnapshot>> LISTENERS = new CopyOnWriteArrayList<>();

    private NextcloudDevEventStore() {
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
     * @param maxEvents        Maximum number of events kept
     * @return Id of the captured event
     */
    static String capture(String handlerId, String rawJson, String eventClass,
            NextcloudUserCredentials credentials, String replayOf, CredentialSource credentialSource,
            int maxEvents) {
        final Entry entry = new Entry(Long.toString(SEQUENCE.incrementAndGet()), handlerId, eventClass, rawJson,
                credentials, replayOf, credentialSource);
        synchronized (ENTRIES) {
            ENTRIES.addFirst(entry);
            while (ENTRIES.size() > Math.max(1, maxEvents)) {
                ENTRIES.removeLast();
            }
        }
        notifyListeners(entry);
        return entry.id;
    }

    /**
     * Marks the processing of an event as finished
     *
     * @param id    Id of the event, no-op if null
     * @param error Error thrown by the handler, null on success
     */
    static void complete(String id, Throwable error) {
        update(id, e -> {
            e.durationMs = System.currentTimeMillis() - e.timestamp;
            if (error != null) {
                e.status = Status.FAILED;
                e.error = stackTrace(error);
            } else {
                e.status = Status.OK;
            }
        });
    }

    /**
     * Sets the status of an event
     *
     * @param id     Id of the event, no-op if null
     * @param status New status
     * @param error  Optional error message
     */
    static void setStatus(String id, Status status, String error) {
        update(id, e -> {
            e.status = status;
            e.error = error;
        });
    }

    /**
     * Marks the ephemeral token of an event as deleted in Nextcloud
     *
     * @param id Id of the event, no-op if null
     */
    static void markTokenRevoked(String id) {
        update(id, e -> e.tokenRevoked = true);
    }

    /**
     * @return All captured events, newest first
     */
    static List<EventSnapshot> list() {
        synchronized (ENTRIES) {
            final List<EventSnapshot> result = new ArrayList<>(ENTRIES.size());
            for (Entry e : ENTRIES) {
                result.add(e.snapshot());
            }
            return result;
        }
    }

    /**
     * Removes all captured events
     */
    static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    static Optional<Entry> get(String id) {
        synchronized (ENTRIES) {
            return ENTRIES.stream().filter(e -> e.id.equals(id)).findFirst();
        }
    }

    static void addListener(Consumer<EventSnapshot> listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Consumer<EventSnapshot> listener) {
        LISTENERS.remove(listener);
    }

    private static void update(String id, Consumer<Entry> change) {
        if (id == null) {
            return;
        }
        get(id).ifPresent(e -> {
            change.accept(e);
            notifyListeners(e);
        });
    }

    private static void notifyListeners(Entry entry) {
        final EventSnapshot snapshot = entry.snapshot();
        for (Consumer<EventSnapshot> listener : LISTENERS) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException e) {
                // A broken Dev UI listener must never affect event processing
            }
        }
    }

    private static String stackTrace(Throwable t) {
        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
