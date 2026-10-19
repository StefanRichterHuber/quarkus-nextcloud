package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@Tag(Scenarios.PASSWORD)
public class CommentTagIT {

    @Test
    public void comments() {
        Scenarios.run("/native/comments");
    }

    @Test
    public void systemTags() {
        Scenarios.run("/native/system-tags");
    }
}
