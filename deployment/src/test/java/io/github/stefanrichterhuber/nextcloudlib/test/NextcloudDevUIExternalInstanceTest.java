package io.github.stefanrichterhuber.nextcloudlib.test;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Dev UI against an externally configured (here: unreachable) Nextcloud. No dev
 * service is started, so credentials must not be exposed and all container
 * actions must be refused.
 */
public class NextcloudDevUIExternalInstanceTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            nextcloud.url=http://localhost:1
                            nextcloud.user=external-user
                            nextcloud.password=external-secret
                            """), "application.properties"));

    public NextcloudDevUIExternalInstanceTest() {
        super("nextcloudlib");
    }

    @Test
    public void instanceInfoHidesExternalCredentials() throws Exception {
        final JsonNode info = executeJsonRPCMethod("getInstanceInfo");
        Assertions.assertEquals("UNAVAILABLE", info.get("mode").asText());
        Assertions.assertFalse(info.get("localDevService").asBoolean());
        Assertions.assertEquals("http://localhost:1", info.get("url").asText());
        Assertions.assertEquals("external-user", info.get("user").asText());
        // The Dev UI JSON mapper omits null values
        Assertions.assertTrue(info.path("password").isMissingNode() || info.get("password").isNull());
        Assertions.assertTrue(info.path("adminPassword").isMissingNode() || info.get("adminPassword").isNull());
        Assertions.assertTrue(info.get("passwordConfigured").asBoolean());
        Assertions.assertEquals("Not reachable", executeJsonRPCMethod("getModeText").asText());
    }

    @Test
    public void containerActionsAreRefused() throws Exception {
        Assertions.assertFalse(executeJsonRPCMethod("getContainerStatus").get("available").asBoolean());
        final JsonNode occ = executeJsonRPCMethod("runOcc", Map.of("command", "app:list"));
        Assertions.assertFalse(occ.get("available").asBoolean());
        Assertions.assertFalse(executeJsonRPCMethod("runWebhookWorker").get("available").asBoolean());
        Assertions.assertFalse(
                executeJsonRPCMethod("setExAppEnabled", Map.of("enabled", "true")).get("available").asBoolean());
    }

    @Test
    public void eventsAndHandlers() throws Exception {
        Assertions.assertEquals(0, executeJsonRPCMethod("getEvents").size());
        Assertions.assertFalse(executeJsonRPCMethod("getHandlers").get("eventsEnabled").asBoolean());
        // Side effects on an external instance require confirmation
        final JsonNode reregister = executeJsonRPCMethod("reregisterWebhooks", Map.of("confirmed", false));
        Assertions.assertFalse(reregister.get("success").asBoolean());
        final JsonNode replay = executeJsonRPCMethod("replay",
                Map.of("id", "unknown", "editedJson", "", "confirmed", false, "password", ""));
        Assertions.assertFalse(replay.get("success").asBoolean());
    }
}
