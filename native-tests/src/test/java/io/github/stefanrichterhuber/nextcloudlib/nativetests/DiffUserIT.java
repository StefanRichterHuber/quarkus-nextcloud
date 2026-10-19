package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@Tag(Scenarios.PASSWORD)
public class DiffUserIT {

    @Test
    public void diff() {
        Scenarios.run("/native/diff");
    }

    @Test
    public void user() {
        Scenarios.run("/native/user");
    }
}
