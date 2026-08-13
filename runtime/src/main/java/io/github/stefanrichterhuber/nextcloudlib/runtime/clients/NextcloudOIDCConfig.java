package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "nextcloud.oidc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface NextcloudOIDCConfig {

    /**
     * Whether to use the user OIDC token for authentication of users instead of the
     * app password. This only works if the app 'user_oidc' is installed on the
     * nextcloud instance and configured to accept the configured OIDC provider. If
     * this is set to true, the app password will be ignored and the token provided
     * by the OIDC provider will be used for authentication. This is more secure and
     * more convenient (since the additional login step is not required) than using
     * an app password, but requires additional configuration on the nextcloud
     * instance.
     * 
     * 
     * @return
     */
    @WithDefault("false")
    boolean enabledForUsers();

    /**
     * Whether to use the user OIDC token for authentication of admin access instead
     * of the app password. This only works if the app 'user_oidc' is installed on
     * the
     * nextcloud instance and configured to accept the configured OIDC provider. If
     * this is set to true, the app password will be ignored and the token provided
     * by the OIDC provider will be used for authentication. This is more secure and
     * more convenient (since the additional login step is not required) than using
     * an app password, but requires additional configuration on the nextcloud
     * instance.
     * 
     * 
     * @return
     */
    @WithDefault("false")
    boolean enabledForAdmins();
}
