package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for the ExApp itself ({@code app.*} properties).
 * Most values are injected as environment variables by the Nextcloud AppAPI
 * daemon when it starts the ExApp container.
 */
@ConfigMapping(prefix = "app")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface NextcloudExappAppConfig {
    /**
     * Shared secret set by the AppAPI host to authenticate communication between
     * Nextcloud and this ExApp.
     *
     * @return the shared secret, or empty if not yet set
     */
    Optional<String> secret();

    /**
     * Unique application identifier registered in Nextcloud.
     *
     * @return app ID, defaults to {@code quarkus-exapp}
     */
    @WithDefault("quarkus-exapp")
    String id();

    /**
     * Human-readable display name shown in the Nextcloud UI.
     *
     * @return display name, defaults to {@code Quarkus ExApp}
     */
    @WithDefault("Quarkus ExApp")
    String displayName();

    /**
     * Version of this ExApp as reported to Nextcloud.
     *
     * @return version string, defaults to {@code 1.0.0}
     */
    @WithDefault("1.0.0")
    String version();

    /**
     * Network protocol used to reach this ExApp from Nextcloud.
     *
     * @return {@code http} or {@code https}, defaults to {@code http}
     */
    @WithDefault("http")
    String protocol();

    /**
     * Hostname or IP address at which Nextcloud can reach this ExApp.
     *
     * @return host, defaults to {@code localhost}
     */
    @WithDefault("localhost")
    String host();

    /**
     * TCP port on which this ExApp listens.
     *
     * @return port number, defaults to {@code 8080}
     */
    @WithDefault("8080")
    Integer port();

    /**
     * Filesystem path used for persistent data storage by this ExApp.
     *
     * @return path to the persistent storage directory
     */
    @WithDefault("/tmp/${app.id}")
    Path persistentStorage();

    /**
     * AppAPI permission scopes granted to this ExApp.
     *
     * @return list of scope identifiers, defaults to all standard scopes
     */
    @WithDefault("SYSTEM,FILES,FILES_SHARING,USER_INFO,USER_STATUS,NOTIFICATIONS,WEATHER_STATUS,TALK,EVENTS_LISTENER")
    List<String> scopes();
}
