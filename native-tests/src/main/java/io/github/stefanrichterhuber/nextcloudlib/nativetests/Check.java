package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import java.io.IOException;
import java.util.Objects;

/**
 * Minimal assertions for the scenarios running inside the native executable
 * (JUnit is not available there). A failed check throws a
 * {@link ScenarioFailedException}, which {@link ScenarioFailureMapper} turns
 * into a HTTP 500 response carrying the message and stack trace.
 */
public final class Check {

    /**
     * Code that is expected to fail with an {@link IOException}
     */
    @FunctionalInterface
    public interface IOAction {
        void run() throws Exception;
    }

    /**
     * Thrown if a check of a scenario fails
     */
    public static class ScenarioFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ScenarioFailedException(String message) {
            super(message);
        }
    }

    private Check() {
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new ScenarioFailedException(message);
        }
    }

    public static void isFalse(boolean condition, String message) {
        isTrue(!condition, message);
    }

    public static <T> T notNull(T value, String message) {
        isTrue(value != null, message);
        return value;
    }

    public static void equal(Object expected, Object actual, String message) {
        isTrue(Objects.equals(expected, actual),
                "%s: expected <%s> but was <%s>".formatted(message, expected, actual));
    }

    /**
     * Checks that the given action fails with an {@link IOException}. Any other
     * exception is passed on, as it indicates a problem (e.g. in the native image)
     * instead of the expected rejection by Nextcloud.
     */
    public static void failsWithIOException(IOAction action, String message) throws Exception {
        try {
            action.run();
        } catch (IOException e) {
            return;
        }
        throw new ScenarioFailedException(message + " (expected an IOException)");
    }
}
