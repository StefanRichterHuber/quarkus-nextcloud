package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@Tag(Scenarios.PASSWORD)
public class WebDavFileIT {

    @Test
    public void uploadListDownload() {
        Scenarios.run("/native/files/upload-list-download");
    }

    @Test
    public void moveCopy() {
        Scenarios.run("/native/files/move-copy");
    }

    @Test
    public void etag() {
        Scenarios.run("/native/files/etag");
    }

    @Test
    public void lock() {
        Scenarios.run("/native/files/lock");
    }

    @Test
    public void revisions() {
        Scenarios.run("/native/files/revisions");
    }

    @Test
    public void search() {
        Scenarios.run("/native/files/search");
    }
}
