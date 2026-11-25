package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient.GetAppPasswordResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

public class OCSMessageTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Failure response as returned by Nextcloud (an empty PHP array as data) */
    private static final String FAILURE = """
            {"ocs":{"meta":{"status":"failure","statuscode":996,"message":"Internal Server Error\\n",\
            "totalitems":"","itemsperpage":""},"data":[]}}""";

    private static String ok(String data) {
        return """
                {"ocs":{"meta":{"status":"ok","statuscode":100,"message":"OK","totalitems":"","itemsperpage":""},\
                "data":%s}}""".formatted(data);
    }

    private static <T> OCSMessage<T> read(String json, TypeReference<OCSMessage<T>> type) {
        return MAPPER.readValue(json, type);
    }

    @Test
    public void emptyArrayForBeanOnFailureIsNull() {
        final OCSMessage<NextcloudUser> msg = read(FAILURE, new TypeReference<>() {
        });
        assertFalse(msg.isOk());
        assertEquals(996, msg.ocs().meta().statuscode());
        assertNull(msg.ocs().data());
    }

    @Test
    public void emptyArrayForScalarIsNull() {
        final OCSMessage<Boolean> msg = read(FAILURE, new TypeReference<>() {
        });
        assertFalse(msg.isOk());
        assertNull(msg.ocs().data());
    }

    @Test
    public void beanIsDeserialized() {
        final OCSMessage<GetAppPasswordResult> msg = read(ok("{\"apppassword\":\"secret\"}"), new TypeReference<>() {
        });
        assertTrue(msg.isOk());
        assertEquals("secret", msg.ocs().data().apppassword());
    }

    @Test
    public void scalarIsDeserialized() {
        final OCSMessage<Boolean> msg = read(ok("true"), new TypeReference<>() {
        });
        assertEquals(Boolean.TRUE, msg.ocs().data());
    }

    @Test
    public void emptyArrayForListStaysEmptyList() {
        final OCSMessage<List<String>> msg = read(ok("[]"), new TypeReference<>() {
        });
        assertEquals(List.of(), msg.ocs().data());
    }

    @Test
    public void listIsDeserialized() {
        final OCSMessage<List<String>> msg = read(ok("[\"a\",\"b\"]"), new TypeReference<>() {
        });
        assertEquals(List.of("a", "b"), msg.ocs().data());
    }

    @Test
    public void emptyArrayForObjectStaysEmptyList() {
        final OCSMessage<Object> msg = read(FAILURE, new TypeReference<>() {
        });
        assertEquals(List.of(), msg.ocs().data());
    }

    @Test
    public void emptyArrayForJsonNodeStaysArrayNode() {
        final OCSMessage<JsonNode> msg = read(FAILURE, new TypeReference<>() {
        });
        assertTrue(msg.ocs().data().isArray());
        assertTrue(msg.ocs().data().isEmpty());
    }

    @Test
    public void nullAndMissingDataAreNull() {
        assertNull(read(ok("null"), new TypeReference<OCSMessage<GetAppPasswordResult>>() {
        }).ocs().data());
        assertNull(read("{\"ocs\":{\"meta\":{\"statuscode\":100}}}", new TypeReference<OCSMessage<GetAppPasswordResult>>() {
        }).ocs().data());
    }

    @Test
    public void nonEmptyArrayForBeanStillFails() {
        assertThrows(MismatchedInputException.class,
                () -> read(ok("[{\"apppassword\":\"secret\"}]"), new TypeReference<OCSMessage<GetAppPasswordResult>>() {
                }));
    }
}
