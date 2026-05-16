package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "app")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface NextcloudExappAppConfig {
    /**
     * Secret set by the ex app host to authenticate the communication between host
     * and this app
     * 
     * @return
     */
    Optional<String> secret();

    /**
     * ID of this app
     * 
     * @return
     */
    Optional<String> id();

    /**
     * Human display name of this app
     * 
     * @return
     */
    Optional<String> displayName();

    /**
     * Version of this app
     * 
     * @return
     */
    Optional<String> version();

    /**
     * Host protocol (either http or https)
     * 
     * @return
     */
    Optional<String> protocol();

    /**
     * EX App host
     * 
     * @return
     */
    Optional<String> host();

    /**
     * EX App host port
     * 
     * @return
     */
    Optional<Integer> port();

    /**
     * Path to the persistent storage of this app
     * 
     * @return
     */
    Optional<Path> persistentStorage();
}
