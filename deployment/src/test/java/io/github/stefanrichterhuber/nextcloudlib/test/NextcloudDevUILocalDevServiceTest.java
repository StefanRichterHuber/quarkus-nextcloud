package io.github.stefanrichterhuber.nextcloudlib.test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Predicate;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Dev UI against the Nextcloud dev service: credentials are exposed, occ
 * commands work, webhook events are captured, replayed and survive hot reload.
 * Requires Docker.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NextcloudDevUILocalDevServiceTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(DevUITestEventHandler.class)
                    // Delete the ephemeral tokens immediately to test the admin fallback on replay
                    .addAsResource(new StringAsset("""
                            quarkus.http.port=8080
                            nextcloud.webhook.cleanup-auth-tokens=true
                            """), "application.properties"));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String OTHER_USER = "devui-user";
    private static final String OTHER_PASSWORD = "Dev-UI-Test-Passw0rd-2026";

    public NextcloudDevUILocalDevServiceTest() {
        super("nextcloudlib");
    }

    /*
     * Note: QuarkusDevModeTest restarts the application (and the dev service) for
     * every test method, so related checks are grouped into few methods.
     */

    @Test
    @Order(1)
    public void instanceToolsAndHandlers() throws Exception {
        final JsonNode info = executeJsonRPCMethod("getInstanceInfo");
        Assertions.assertEquals("LOCAL_DEV_SERVICE", info.get("mode").asText());
        Assertions.assertTrue(info.get("localDevService").asBoolean());
        Assertions.assertFalse(info.get("password").asText().isEmpty());
        Assertions.assertTrue(info.get("containerId").isTextual());
        Assertions.assertEquals("Local dev service", executeJsonRPCMethod("getModeText").asText());
        Assertions.assertTrue(executeJsonRPCMethod("getContainerStatus").get("available").asBoolean());

        // occ console
        final JsonNode result = executeJsonRPCMethod("runOcc", Map.of("command", "occ app:list --output=json"));
        Assertions.assertTrue(result.get("available").asBoolean());
        Assertions.assertEquals(0, result.get("exitCode").asInt());
        Assertions.assertTrue(result.get("stdout").asText().contains("webhook_listeners"));

        // Handlers and their registered webhooks
        final JsonNode handlers = executeJsonRPCMethod("getHandlers");
        Assertions.assertTrue(handlers.get("eventsEnabled").asBoolean());
        Assertions.assertEquals(1, handlers.get("handlers").size());
        final JsonNode handler = handlers.get("handlers").get(0);
        Assertions.assertTrue(handler.get("requestAuthToken").asBoolean());
        Assertions.assertEquals(1, handler.get("registeredWebhooks").size());
    }

    @Test
    @Order(2)
    public void eventsAreCapturedReplayedAndSurviveHotReload() throws Exception {
        // The webhook background worker of the dev service delivers the events
        upload("devui-ok.txt");
        upload("devui-fail.txt");

        final JsonNode ok = awaitEvent(e -> e.get("json").asText().contains("devui-ok.txt")
                && !e.get("status").asText().equals("PENDING"));
        Assertions.assertEquals("OK", ok.get("status").asText());
        Assertions.assertEquals("ORIGINAL", ok.get("credentialSource").asText());
        // Ephemeral tokens are masked
        Assertions.assertTrue(ok.get("json").asText().contains("\"token\" : \"********\""),
                "token must be masked: " + ok.get("json").asText());

        final JsonNode failed = awaitEvent(e -> e.get("json").asText().contains("devui-fail.txt")
                && !e.get("status").asText().equals("PENDING"));
        Assertions.assertEquals("FAILED", failed.get("status").asText());
        Assertions.assertTrue(failed.get("error").asText().contains("Handler failure"));

        // Token was deleted after processing (cleanup-auth-tokens=true) -> temporary
        // app password of the original user (admin, password from the config), deleted
        // again after the replay
        final String okId = ok.get("id").asText();
        awaitEvent(e -> e.get("id").asText().equals(okId) && e.get("tokenRevoked").asBoolean());
        final JsonNode replay = executeJsonRPCMethod("replay",
                Map.of("id", okId, "editedJson", "", "confirmed", false, "password", ""));
        Assertions.assertTrue(replay.get("success").asBoolean(), replay.toString());
        Assertions.assertEquals("TEMPORARY", replay.get("credentialSource").asText());
        final String replayId = replay.get("id").asText();
        final JsonNode replayed = awaitEvent(e -> e.get("id").asText().equals(replayId)
                && !e.get("status").asText().equals("PENDING"));
        Assertions.assertEquals("OK", replayed.get("status").asText());
        Assertions.assertEquals(okId, replayed.get("replayOf").asText());
        Assertions.assertEquals("admin", replayed.get("triggerUser").asText());
        awaitEvent(e -> e.get("id").asText().equals(replayId) && e.get("tokenRevoked").asBoolean());
        assertNoReplayAppPasswordLeft("admin");

        // Event of a user without configured password: the password must be entered
        createUser(OTHER_USER, OTHER_PASSWORD);
        upload(OTHER_USER, OTHER_PASSWORD, "devui-other.txt");
        final JsonNode other = awaitEvent(e -> e.get("json").asText().contains("devui-other.txt")
                && e.get("tokenRevoked").asBoolean());
        Assertions.assertEquals(OTHER_USER, other.get("triggerUser").asText());
        final String otherId = other.get("id").asText();

        final JsonNode noPassword = executeJsonRPCMethod("replay",
                Map.of("id", otherId, "editedJson", "", "confirmed", false, "password", ""));
        Assertions.assertFalse(noPassword.get("success").asBoolean());
        Assertions.assertTrue(noPassword.get("passwordRequired").asBoolean(), noPassword.toString());
        Assertions.assertEquals(OTHER_USER, noPassword.get("user").asText());

        final JsonNode wrongPassword = executeJsonRPCMethod("replay",
                Map.of("id", otherId, "editedJson", "", "confirmed", false, "password", "wrong-password"));
        Assertions.assertFalse(wrongPassword.get("success").asBoolean());
        Assertions.assertTrue(wrongPassword.get("passwordRequired").asBoolean(), wrongPassword.toString());

        final JsonNode withPassword = executeJsonRPCMethod("replay",
                Map.of("id", otherId, "editedJson", "", "confirmed", false, "password", OTHER_PASSWORD));
        Assertions.assertTrue(withPassword.get("success").asBoolean(), withPassword.toString());
        Assertions.assertEquals("TEMPORARY", withPassword.get("credentialSource").asText());
        final String otherReplayId = withPassword.get("id").asText();
        final JsonNode otherReplayed = awaitEvent(e -> e.get("id").asText().equals(otherReplayId)
                && !e.get("status").asText().equals("PENDING") && e.get("tokenRevoked").asBoolean());
        Assertions.assertEquals("OK", otherReplayed.get("status").asText());
        Assertions.assertEquals(OTHER_USER, otherReplayed.get("triggerUser").asText());
        assertNoReplayAppPasswordLeft(OTHER_USER);

        // The entered password is kept for further replays
        final JsonNode remembered = executeJsonRPCMethod("replay",
                Map.of("id", otherId, "editedJson", "", "confirmed", false, "password", ""));
        Assertions.assertTrue(remembered.get("success").asBoolean(), remembered.toString());

        // Edit & replay: the edited event makes the handler fail
        final String edited = ok.get("json").asText().replace("devui-ok.txt", "devui-fail-edited.txt");
        final JsonNode editedReplay = executeJsonRPCMethod("replay",
                Map.of("id", okId, "editedJson", edited, "confirmed", false, "password", ""));
        final String editedId = editedReplay.get("id").asText();
        final JsonNode editedEvent = awaitEvent(e -> e.get("id").asText().equals(editedId)
                && !e.get("status").asText().equals("PENDING"));
        Assertions.assertEquals("FAILED", editedEvent.get("status").asText());
        Assertions.assertTrue(editedEvent.get("error").asText().contains("devui-fail-edited.txt"));

        // Invalid JSON is rejected
        final JsonNode invalid = executeJsonRPCMethod("replay",
                Map.of("id", okId, "editedJson", "{ not json", "confirmed", false, "password", ""));
        Assertions.assertFalse(invalid.get("success").asBoolean());

        // Events survive a hot reload
        final int before = executeJsonRPCMethod("getEvents").size();
        Assertions.assertTrue(before > 0);
        devModeTest.modifySourceFile(DevUITestEventHandler.class, s -> s.replace("Handler failure", "Failure"));
        // Any request triggers the reload
        HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:8080/")).build(),
                HttpResponse.BodyHandlers.discarding());
        Assertions.assertEquals(before, executeJsonRPCMethod("getEvents").size());
    }

    private void upload(String name) throws Exception {
        final JsonNode info = executeJsonRPCMethod("getInstanceInfo");
        upload(info.get("user").asText(), info.get("password").asText(), name);
    }

    private void upload(String user, String password, String name) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(
                URI.create(nextcloudUrl() + "/remote.php/dav/files/" + user + "/" + name))
                .header("Authorization", basicAuth(user, password))
                .PUT(HttpRequest.BodyPublishers.ofString("Dev UI test"))
                .build();
        final HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        Assertions.assertTrue(response.statusCode() < 300, "Upload failed with HTTP " + response.statusCode());
    }

    /**
     * Creates a Nextcloud user with the provisioning API
     */
    private void createUser(String user, String password) throws Exception {
        final JsonNode info = executeJsonRPCMethod("getInstanceInfo");
        final String body = "userid=" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(nextcloudUrl() + "/ocs/v1.php/cloud/users"))
                .header("Authorization", basicAuth(info.get("user").asText(), info.get("password").asText()))
                .header("OCS-APIRequest", "true")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertTrue(response.statusCode() < 300 && response.body().contains("<statuscode>100</statuscode>"),
                "Creating user failed: " + response.body());
    }

    private void assertNoReplayAppPasswordLeft(String user) throws Exception {
        final JsonNode tokens = executeJsonRPCMethod("runOcc",
                Map.of("command", "user:auth-tokens:list " + user + " --output=json"));
        Assertions.assertEquals(0, tokens.get("exitCode").asInt(), tokens.toString());
        Assertions.assertFalse(tokens.get("stdout").asText().contains("Quarkus Dev UI event replay"),
                tokens.toString());
    }

    private String nextcloudUrl() throws Exception {
        return executeJsonRPCMethod("getInstanceInfo").get("url").asText();
    }

    private static String basicAuth(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode awaitEvent(Predicate<JsonNode> condition) throws Exception {
        final long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            for (JsonNode e : executeJsonRPCMethod("getEvents")) {
                if (condition.test(e)) {
                    return e;
                }
            }
            Thread.sleep(1000);
        }
        Assertions.fail("Event not received in time: " + executeJsonRPCMethod("getEvents"));
        return null;
    }
}
