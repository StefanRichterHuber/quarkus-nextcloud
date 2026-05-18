package io.github.stefanrichterhuber.nextcloudlib.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.FileEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * End-to-end test that verifies Nextcloud delivers webhook events to the
 * running application after a file operation.
 *
 * <p>
 * Requires the dev-services container to be started with
 * {@code webhook_listeners}
 * app enabled and the webhook worker active. See
 * {@code src/test/resources/application.properties}.
 * </p>
 */
@QuarkusTest
// @Disabled
public class WebhookEventTest {

    private static final String TEST_DIR = "/TESTDIR-webhook";
    private static final int WEBHOOK_TIMEOUT_SECONDS = 30;

    @Inject
    NextcloudFileService fileService;

    @Inject
    WebhookEventCaptor captor;

    @BeforeEach
    void reset() {
        captor.reset();
    }

    @Test
    void fileCreateTriggersWebhookEvent() throws Exception {
        // Wait for the enabling of the app
        Thread.sleep(20 * 1000);
        fileService.createDirectories(TEST_DIR);
        String filename = TEST_DIR + "/" + UUID.randomUUID() + ".txt";

        fileService.uploadFile(filename, "text/plain",
                new ByteArrayInputStream("webhook test".getBytes(StandardCharsets.UTF_8)));
        try {
            Thread.sleep(WEBHOOK_TIMEOUT_SECONDS * 1000);
            List<NextcloudEvent<?>> events = captor.receivedEvents();
            assertTrue(events.stream().map(ne -> ne.event().className())
                    .anyMatch(ev -> Objects.equals(NextcloudEvent.FileNodeCreatedEvent, ev)));
            assertTrue(events.stream().map(ne -> ne.event().className())
                    .anyMatch(ev -> Objects.equals(NextcloudEvent.FileNodeWrittenEvent, ev)));

            assertTrue(
                    events.stream().map(ne -> (FileEvent) ne.event())
                            .anyMatch(fe -> fe.node().path().endsWith(filename)));

        } finally {
            fileService.deleteFile(filename, null, (String) null);
        }
    }
}
