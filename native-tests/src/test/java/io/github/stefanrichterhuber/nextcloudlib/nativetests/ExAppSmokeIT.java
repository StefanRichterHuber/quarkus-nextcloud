package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Smoke test of the binary built with ExApp support: lifecycle routes and the
 * WebDAV / CalDAV paths with ExApp authentication.
 */
@QuarkusIntegrationTest
@TestProfile(ExAppDevServiceProfile.class)
@Tag(Scenarios.EXAPP)
public class ExAppSmokeIT {

    @Test
    public void heartbeat() {
        given().when().get("/heartbeat").then().statusCode(200);
    }

    @Test
    public void uploadListDownload() {
        Scenarios.run("/native/files/upload-list-download");
    }

    @Test
    public void search() {
        Scenarios.run("/native/files/search");
    }

    @Test
    public void calendar() {
        Scenarios.run("/native/pim/calendar");
    }
}
