package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@Tag(Scenarios.PASSWORD)
public class EventIT {

    @Test
    public void fileEvents() {
        Scenarios.run("/native/events/files");
    }

    @Test
    public void systemTagEvents() {
        Scenarios.run("/native/events/system-tags");
    }

    @Test
    public void calendarEvents() {
        Scenarios.run("/native/events/calendar");
    }

    @Test
    public void filterExpression() {
        Scenarios.run("/native/events/filter");
    }
}
