package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.regex.Pattern;

import io.restassured.response.Response;

/**
 * Triggers a scenario running inside the native executable. A failed scenario
 * responds with HTTP 500 and the stack trace from the binary.
 */
final class Scenarios {
    /** JUnit tag of the tests run against the binary built with app password auth */
    static final String PASSWORD = "password";
    /** JUnit tag of the tests run against the binary built with ExApp auth */
    static final String EXAPP = "exapp";

    /**
     * The Nextcloud dev service uses SQLite, which occasionally rejects requests
     * with "database is locked" (HTTP 503) under load, e.g. while it dispatches
     * webhooks. Scenarios failing with a 503 from Nextcloud are retried, all
     * other failures are reported immediately.
     */
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern NEXTCLOUD_UNAVAILABLE = Pattern.compile("status code:? 503|database is locked");

    private Scenarios() {
    }

    static void run(String path) {
        Response response = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            response = given().when().post(path);
            if (response.statusCode() == 204 || !NEXTCLOUD_UNAVAILABLE.matcher(response.asString()).find()) {
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        final Response result = response;
        assertEquals(204, result.statusCode(), () -> "Scenario " + path + " failed:\n" + result.asString());
    }
}
