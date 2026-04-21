package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default implementation of NextcloudAuthProvider that reads the credentials
 * from the configuration. This bean is only used if no custom
 * NextcloudAuthProvider is provided by the user. The required configuration
 * properties are:
 * <ul>
 * <li>nextcloud.url: Root url of the nextcloud installation (e.g.
 * 'https://nextcloud.example.com')</li>
 * <li>nextcloud.appName: Name of this application (required to get the correct
 * app password for the nextcloud rest api)</li>
 * <li>nextcloud.user: Username for the nextcloud installation</li>
 * <li>nextcloud.password: Password for the nextcloud installation</li>
 * </ul>
 */
@DefaultBean
@ApplicationScoped
@NextcloudAdmin
public class ConfiguredNextcloudAdminAuthProvider extends ConfiguredNextcloudAuthProvider {
}
