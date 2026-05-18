package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.auth;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;

/**
 * ExApp-specific {@link io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider}
 * qualified with {@link io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin}.
 * Inherits all credential resolution from {@link ExAppNextcloudAuthProvider} and
 * is injected wherever an admin-scoped provider is required in ExApp mode.
 */
@DefaultBean
@RequestScoped
@Priority(ExAppNextcloudAuthProvider.PRIORITY)
@NextcloudAdmin
public class ExAppNextcloudAdminAuthProvider extends ExAppNextcloudAuthProvider {

}
