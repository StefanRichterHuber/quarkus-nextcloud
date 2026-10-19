package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@Tag(Scenarios.PASSWORD)
public class PimIT {

    @Test
    public void calendar() {
        Scenarios.run("/native/pim/calendar");
    }

    @Test
    public void contacts() {
        Scenarios.run("/native/pim/contacts");
    }
}
