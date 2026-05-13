package io.github.stefanrichterhuber.nextcloudlib.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
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
public class WebhookEventTest {

    private static final String TEST_DIR = "/TESTDIR-webhook";
    private static final int WEBHOOK_TIMEOUT_SECONDS = 20;

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
        fileService.createDirectories(TEST_DIR);
        String filename = TEST_DIR + "/" + UUID.randomUUID() + ".txt";

        fileService.uploadFile(filename, "text/plain",
                new ByteArrayInputStream("webhook test".getBytes(StandardCharsets.UTF_8)));
        try {
            NextcloudEvent<?> event = captor.poll(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertNotNull(event, "Expected a webhook event within " + WEBHOOK_TIMEOUT_SECONDS + "s");
            assertEquals(NextcloudEvent.FileNodeCreatedEvent, event.event().className());
        } finally {
            fileService.deleteFile(filename, null, (String) null);
        }
    }

    @Test
    void fileDeleteTriggersWebhookEvent() throws Exception {
        fileService.createDirectories(TEST_DIR);
        String filename = TEST_DIR + "/" + UUID.randomUUID() + ".txt";

        fileService.uploadFile(filename, "text/plain",
                new ByteArrayInputStream("webhook delete test".getBytes(StandardCharsets.UTF_8)));

        // Drain any creation event before testing the delete
        captor.poll(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        captor.reset();

        fileService.deleteFile(filename, null, (String) null);

        NextcloudEvent<?> event = captor.poll(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertNotNull(event, "Expected a delete webhook event within " + WEBHOOK_TIMEOUT_SECONDS + "s");
        assertEquals(NextcloudEvent.FileNodeDeletedEvent, event.event().className());
    }
}
