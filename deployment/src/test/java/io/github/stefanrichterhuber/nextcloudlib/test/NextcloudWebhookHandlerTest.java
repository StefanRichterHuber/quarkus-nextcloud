package io.github.stefanrichterhuber.nextcloudlib.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Verifies the Vert.x webhook endpoint wired up by the extension when at least
 * one {@link OnNextcloudEvent}-annotated method is present.
 *
 * <p>
 * The test builds a minimal Quarkus application that contains
 * {@link TestEventCollector} (an {@code @ApplicationScoped} bean with a
 * {@link OnNextcloudEvent} handler). The extension discovers the handler at
 * build time and registers the {@code /webhook} endpoint and a startup
 * webhook-registrar bean. The registrar's attempt to reach Nextcloud fails
 * gracefully because {@code nextcloud.url} is not configured, so startup
 * succeeds.
 * </p>
 */
public class NextcloudWebhookHandlerTest {

    static final String SECRET = "test-secret-abc123";

    /** A fixed Nextcloud {@code NodeCreatedEvent} payload matching the Event deserialization test. */
    static final String FILE_CREATED_BODY = """
            {
              "event": {
                "class": "OCP\\\\Files\\\\Events\\\\Node\\\\NodeCreatedEvent",
                "node": {
                  "id": 437,
                  "path": "/admin/files/test-webhook.txt"
                }
              },
              "user": {
                "uid": "admin",
                "displayName": "Admin"
              },
              "time": 1700100000
            }
            """;

    @RegisterExtension
    static final QuarkusUnitTest quarkus = new QuarkusUnitTest()
            .overrideConfigKey("nextcloud.webhook.secret", SECRET)
            // Use a dedicated port to avoid clashing with other tests
            .overrideConfigKey("quarkus.http.test-port", "18843")
            .withApplicationRoot(jar -> jar.addClasses(TestEventCollector.class));

    @Inject
    TestEventCollector collector;

    @BeforeEach
    void reset() {
        collector.reset();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void webhookWithValidSecretDispatchesEvent() throws Exception {
        int status = post(SECRET, FILE_CREATED_BODY);

        assertEquals(200, status);
        assertEquals(1, collector.eventCount());
        assertFalse(collector.receivedEvents().isEmpty());
        assertEquals(NextcloudEvent.FileNodeCreatedEvent,
                collector.receivedEvents().get(0).event().className());
    }

    // -------------------------------------------------------------------------
    // Security: wrong / missing secret
    // -------------------------------------------------------------------------

    @Test
    void webhookWithWrongSecretReturns401() throws Exception {
        int status = post("wrong-secret", FILE_CREATED_BODY);

        assertEquals(401, status);
        assertEquals(0, collector.eventCount());
    }

    @Test
    void webhookWithMissingSecretHeaderReturns401() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(webhookUri())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(FILE_CREATED_BODY))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(401, response.statusCode());
        assertEquals(0, collector.eventCount());
    }

    // -------------------------------------------------------------------------
    // Malformed body
    // -------------------------------------------------------------------------

    @Test
    void webhookWithInvalidJsonReturns500() throws Exception {
        int status = post(SECRET, "{ not valid json");

        assertEquals(500, status);
        assertEquals(0, collector.eventCount());
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private static URI webhookUri() {
        return URI.create("http://localhost:18843/webhook");
    }

    private static int post(String secret, String body) throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(webhookUri())
                .header("Content-Type", "application/json")
                .header("X-Nextcloud-Webhook-Secret", secret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // -------------------------------------------------------------------------
    // Test CDI bean
    // -------------------------------------------------------------------------

    /**
     * An application-scoped CDI bean with an {@link OnNextcloudEvent} handler.
     * The extension discovers this class at build time (via
     * {@code withApplicationRoot}) and wires it into the webhook dispatch chain.
     */
    @ApplicationScoped
    public static class TestEventCollector {

        private final AtomicInteger count = new AtomicInteger();
        private final List<NextcloudEvent<?>> events =
                Collections.synchronizedList(new ArrayList<>());

        @OnNextcloudEvent(NextcloudEvent.FileNodeCreatedEvent)
        public void onFileCreated(NextcloudEvent<?> event) {
            count.incrementAndGet();
            events.add(event);
        }

        public int eventCount() {
            return count.get();
        }

        public List<NextcloudEvent<?>> receivedEvents() {
            return Collections.unmodifiableList(events);
        }

        public void reset() {
            count.set(0);
            events.clear();
        }
    }
}
