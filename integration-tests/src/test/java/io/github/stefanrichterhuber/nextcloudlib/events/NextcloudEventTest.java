package io.github.stefanrichterhuber.nextcloudlib.events;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.profiles.EventHandlerTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(EventHandlerTestProfile.class)
public class NextcloudEventTest {
    private static final String ROOT_DIR = "/TESTDIR_FOR_EVENTS";

    private final static String TEST_TEXT1 = """
            # Ode to the Cloud

            Up in the servers, quiet and vast,
            Where files are stored and memories last,
            A markdown file in a folder sleeps,
            While Nextcloud faithfully its promise keeps.

            Through tunnels of light the data flows,
            Past patches and diffs, the revision grows,
            Each change a whisper, each save a breath,
            A document lives its little life past death.

            So here's to the cloud, both humble and bright,
            That keeps our small poems through day and through night.

                        """;

    @Inject
    NextcloudFileService fileService;

    @Inject
    TestEventHandlers testEventHandlers;

    @Inject
    NextcloudAuthProvider authProvider;

    @Inject
    NextcloudUserService userService;

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
    private NextcloudEvent<NextcloudEvent.FileEvent> waitForEvent(String className,
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
     * Tests if the given credentials can be used to access the current user info
     * 
     * @param credentials The credentials to test
     * @return true if the credentials are valid and can be used to access the
     *         current user info, false otherwise
     */
    private boolean testCredentials(NextcloudUserCredentials credentials) {
        final Executor executor = new CredentialsAwareRequestScopedExecutor(Executors.newFixedThreadPool(1),
                credentials);
        final CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
            if (!authProvider.getCredentials().equals(credentials)) {
                return false;
            }

            NextcloudUser nextcloudUser = userService.getCurrentUserInfo();
            if (nextcloudUser == null) {
                return false;
            }
            return true;
        }, executor);
        return result.join();
    }

    @Test
    public void testEventHandler() throws IOException, InterruptedException {
        int timeoutSeconds = 30;
        final String user = authProvider.getUser();
        fileService.createDirectories(ROOT_DIR);

        // First event should be triggered by the creation of the directory
        final NextcloudEvent<NextcloudEvent.FileEvent> event1 = waitForEvent(NextcloudEvent.FileNodeCreatedEvent,
                event -> event.event().node().path().equals("/" + user + "/files" + ROOT_DIR), timeoutSeconds);

        assertNotNull(event1, "Node create event for creation of directory was not received");

        final String rawFileName = UUID.randomUUID().toString() + "-test.md";
        final String filename = ROOT_DIR + "/" + rawFileName;
        fileService.uploadFile(filename, "text/markdown",
                new ByteArrayInputStream(TEST_TEXT1.getBytes(StandardCharsets.UTF_8)));

        // Second event should be triggered by the creation of the file
        final NextcloudEvent<NextcloudEvent.FileEvent> event2 = waitForEvent(NextcloudEvent.FileNodeCreatedEvent,
                event -> event.event().node().path().equals("/" + user + "/files" + filename), timeoutSeconds);

        assertNotNull(event2, "Node create event for creation of file was not received");

        // Use the credentials provided by the event to access the file service
        // Unfortunately the server returned by the event does not contain the correct
        // port mapped from the docker container, so we have to manually set the correct
        // server here.
        final NextcloudUserCredentials credentials = event2.authentication().trigger().toUserCredentials()
                .withServer(authProvider.getServer());
        assertTrue(testCredentials(credentials),
                "Unable to perform request with credentials provided by event authentication");

        // Third event should be triggered by the deletion of the file
        fileService.deleteFile(filename);
        final NextcloudEvent<NextcloudEvent.FileEvent> event3 = waitForEvent(NextcloudEvent.FileNodeDeletedEvent,
                event -> event.event().node().path().equals("/" + user + "/files" + filename), timeoutSeconds);

        assertNotNull(event3, "Node delete event for deletion of file was not received");

        // Fourth event should be triggered by the deletion of the directory
        fileService.deleteFile(ROOT_DIR);
        final NextcloudEvent<NextcloudEvent.FileEvent> event4 = waitForEvent(NextcloudEvent.FileNodeDeletedEvent,
                event -> event.event().node().path().equals("/" + user + "/files" + ROOT_DIR), timeoutSeconds);

        assertNotNull(event4, "Node delete event for deletion of folder was not received");

    }

}
