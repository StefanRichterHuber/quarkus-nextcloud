package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.Optional;

/**
 * Holds the Nextcloud container started by
 * {@link NextcloudDevServicesResultBuildItem}, so that Dev UI build time
 * actions (occ console, webhook worker, ExApp enable/disable) can access it.
 *
 * <p>
 * Only containers started by this extension are held here. If an external
 * Nextcloud is configured ({@code nextcloud.url}), nothing is held and all
 * container actions are unavailable.
 * </p>
 */
final class NextcloudDevServiceState {

    private static volatile NextcloudContainer container;
    private static volatile String exAppId;

    private NextcloudDevServiceState() {
    }

    /**
     * Sets the started dev service container
     *
     * @param started Container started, null to clear
     */
    static void setContainer(NextcloudContainer started) {
        container = started;
        exAppId = null;
    }

    /**
     * Sets the id of the ExApp registered in the dev service
     *
     * @param id ExApp id
     */
    static void setExAppId(String id) {
        exAppId = id;
    }

    /**
     * @return The dev service container, if one was started by this extension and
     *         it is still running. Checked on every call, so a stopped container
     *         is reported as unavailable.
     */
    static Optional<NextcloudContainer> runningContainer() {
        final NextcloudContainer c = container;
        return c != null && c.isRunning() ? Optional.of(c) : Optional.empty();
    }

    /**
     * @return Id of the ExApp registered in the dev service, if any
     */
    static Optional<String> exAppId() {
        return Optional.ofNullable(exAppId);
    }
}
