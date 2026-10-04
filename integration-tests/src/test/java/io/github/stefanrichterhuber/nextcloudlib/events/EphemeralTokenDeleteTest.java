package io.github.stefanrichterhuber.nextcloudlib.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.events.TestEventHandlers.PendingAsyncEvent;
import io.github.stefanrichterhuber.nextcloudlib.other.NextcloudController;
import io.github.stefanrichterhuber.nextcloudlib.other.NextcloudController.UserAuthToken;
import io.github.stefanrichterhuber.nextcloudlib.profiles.EventHandlerWithEphemeralTokenDeleteTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(EventHandlerWithEphemeralTokenDeleteTestProfile.class)
public class EphemeralTokenDeleteTest {

    private static final String ROOT_DIR = "/TESTDIR_FOR_EVENTS_WITH_DELETED_TOKENS";

    private static final String NAME_EPHEMERAL_WEBHOOK_TOKEN = "Ephemeral webhook authentication";

    private static final int TIMEOUT_SECONDS = 30;

    @Inject
    @ConfigProperty(name = "nextcloud.webhook.cleanup-auth-token-delay")
    Duration cleanupDelay;

    @Inject
    NextcloudController controller;

    @Inject
    NextcloudAuthProvider authProvider;

    @Inject
    NextcloudFileService fileService;

    @Inject
    NextcloudUserService userService;

    @Inject
    TestEventHandlers testEventHandlers;

    /**
     * Waits for an event of the given class name and predicate to be received by
     * the test event
     * handler and returns it if found within the timeout
     *
     * @param className      The class name of the event to wait for
     * @param predicate      The predicate to test the event against
     * @param timeoutSeconds The timeout in seconds
     * @return The found event or null if not found
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    private NextcloudEvent<NextcloudEvent.FileEvent> waitForFileEvent(String className,
            Predicate<NextcloudEvent<NextcloudEvent.FileEvent>> predicate,
            int timeoutSeconds) throws InterruptedException {
        int rounds = timeoutSeconds;
        while (rounds > 0) {
            for (NextcloudEvent<NextcloudEvent.FileEvent> event : testEventHandlers.getReceivedFileEvents()) {
                if (event.event().className().equals(className) && predicate.test(event)) {
                    return event;
                }
            }
            Thread.sleep(1000);
            rounds--;
        }
        return null;
    }

    /**
     * Waits for an event received by one of the asynchronous test handlers
     *
     * @param events         Supplier of the events received by the handler
     * @param path           Path of the node of the event
     * @param timeoutSeconds The timeout in seconds
     * @return The found event or null if not found
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    private PendingAsyncEvent waitForAsyncEvent(Supplier<List<PendingAsyncEvent>> events, String path,
            int timeoutSeconds) throws InterruptedException {
        int rounds = timeoutSeconds;
        while (rounds > 0) {
            for (PendingAsyncEvent event : events.get()) {
                if (event.event().event().node().path().equals(path)) {
                    return event;
                }
            }
            Thread.sleep(1000);
            rounds--;
        }
        return null;
    }

    /**
     * Polls the given condition every second until it is fulfilled or the timeout
     * runs out
     *
     * @return {@code true} if the condition was fulfilled within the timeout
     */
    private static boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        final long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(1000);
        }
        return condition.getAsBoolean();
    }

    /**
     * Checks whether the given credentials are (still) accepted by the Nextcloud
     * server
     */
    private boolean testCredentials(NextcloudUserCredentials credentials) {
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return userService.getCurrentUserInfo() != null;
                } catch (Exception e) {
                    return false;
                }
            }, new CredentialsAwareRequestScopedExecutor(pool, credentials)).join();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether the automatic cleanup process of ephemeral auth token issued
     * with each event is working. This is a test for workaround necessary because
     * the automatic clean up within nextcloud server is not working at all
     *
     * @see <a href="https://github.com/nextcloud/server/issues/64682">Github-Issue
     *      64682</a>
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testCleanupToken() throws IOException, InterruptedException {
        // Other tests may leave tokens behind (e.g. still waiting for their cleanup
        // delay), so only consider tokens created by this test
        final Set<Integer> tokensBefore = listEphemeralTokenIds();

        final String user = authProvider.getUser();
        fileService.createDirectories(ROOT_DIR);
        try {
            // First event should be triggered by the creation of the directory
            final NextcloudEvent<NextcloudEvent.FileEvent> event1 = waitForFileEvent(
                    NextcloudEvent.FileNodeCreatedEvent,
                    event -> event.event().node().path().equals("/" + user + "/files" + ROOT_DIR), TIMEOUT_SECONDS);

            assertNotNull(event1, "Node create event for creation of directory was not received");

            final Set<Integer> newTokens = listEphemeralTokenIds();
            newTokens.removeAll(tokensBefore);
            assertFalse(newTokens.isEmpty(), "No ephemeral token was created for the event");

            // Wait until the expected clean up delay runs out (and add some seconds to give
            // the background calls enough time)
            assertTrue(waitUntil(() -> {
                final Set<Integer> remaining = listEphemeralTokenIds();
                remaining.retainAll(newTokens);
                return remaining.isEmpty();
            }, cleanupDelay.plusSeconds(20)), "Ephemeral tokens were not deleted after the cleanup delay");
        } finally {
            fileService.deleteFile(ROOT_DIR);
        }
    }

    /**
     * Tests that the ephemeral token of a handler returning a
     * {@link CompletableFuture} is not deleted before the returned future completes
     */
    @Test
    public void testCleanupWaitsForCompletionStage() throws IOException, InterruptedException {
        testCleanupWaitsForAsyncHandler(TestEventHandlers.ASYNC_STAGE_DIR, testEventHandlers::getAsyncStageEvents);
    }

    /**
     * Tests that the ephemeral token of a handler returning a {@code Uni} is not
     * deleted before the returned Uni completes
     */
    @Test
    public void testCleanupWaitsForUni() throws IOException, InterruptedException {
        testCleanupWaitsForAsyncHandler(TestEventHandlers.ASYNC_UNI_DIR, testEventHandlers::getAsyncUniEvents);
    }

    private void testCleanupWaitsForAsyncHandler(String dir, Supplier<List<PendingAsyncEvent>> events)
            throws IOException, InterruptedException {
        final String user = authProvider.getUser();
        fileService.createDirectories(dir);
        try {
            final PendingAsyncEvent pending = waitForAsyncEvent(events, "/" + user + "/files" + dir,
                    TIMEOUT_SECONDS);
            assertNotNull(pending, "Node create event for creation of directory was not received");

            // The handler method has returned, but its result is still pending: the token
            // must survive well beyond the cleanup delay
            Thread.sleep(cleanupDelay.plusSeconds(5));
            assertTrue(testCredentials(pending.credentials()),
                    "Ephemeral token was deleted before the asynchronous handler completed");

            // Finish the handler -> token must be deleted after the cleanup delay
            pending.completion().complete(null);
            assertTrue(waitUntil(() -> !testCredentials(pending.credentials()), cleanupDelay.plusSeconds(20)),
                    "Ephemeral token was not deleted after the asynchronous handler completed");
        } finally {
            fileService.deleteFile(dir);
        }
    }

    private Set<Integer> listEphemeralTokenIds() {
        return listEphemeralTokens().stream().map(UserAuthToken::id).collect(Collectors.toSet());
    }

    public List<UserAuthToken> listEphemeralTokens() {
        final String user = authProvider.getUser();
        return controller.listAuthTokens(user).join().stream()
                .filter(u -> u.name().equals(NAME_EPHEMERAL_WEBHOOK_TOKEN))
                .toList();
    }
}
