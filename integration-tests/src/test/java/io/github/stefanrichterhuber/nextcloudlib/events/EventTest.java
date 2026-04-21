package io.github.stefanrichterhuber.nextcloudlib.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class EventTest {

  public static final String SINGLE_NODE_FILE_EVENT = """
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

  @Inject
  ObjectMapper objectMapper;

  @Test
  public void testEventDeserialization() throws com.fasterxml.jackson.core.JsonProcessingException {
    NextcloudEvent<Event> event = objectMapper.readValue(SINGLE_NODE_FILE_EVENT,
        new TypeReference<NextcloudEvent<Event>>() {
        });
    assertInstanceOf(NextcloudEvent.FileEvent.class, event.event());
  }
}
