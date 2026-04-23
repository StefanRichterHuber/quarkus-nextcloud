package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.security.Permission;
import java.security.Principal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.SecurityContext;

/**
 * SecurityContext implementation derived from a NextcloudUser. This is used to
 * integrate with Quarkus' security framework and provide authentication and
 * authorization based on Nextcloud user information.
 */
public class NextcloudSecurityIdentity implements SecurityIdentity, SecurityContext {
    private static final ObjectMapper om = new ObjectMapper();
    private final NextcloudUser user;
    private final NextcloudUserCredentials credentials;
    private final Map<String, Object> objectMap;
    private final Set<Permission> permissions;

    public NextcloudSecurityIdentity(NextcloudUser user, NextcloudUserCredentials credentials,
            Collection<? extends Permission> permissions) {
        this.user = user;
        this.credentials = credentials;
        this.permissions = permissions != null ? new HashSet<>(permissions) : Set.of();
        this.objectMap = om.convertValue(user, new TypeReference<Map<String, Object>>() {
        });
    }

    @Override
    public Principal getPrincipal() {
        return new NextcloudSecurityPrincipal(user);
    }

    @Override
    public boolean isAnonymous() {
        return false;
    }

    @Override
    public Set<String> getRoles() {
        return new HashSet<>(user.groups());
    }

    @Override
    public boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    @Override
    public Set<Permission> getPermissions() {
        return new HashSet<>(permissions);
    }

    @Override
    public <T extends Credential> T getCredential(Class<T> credentialType) {
        if (NextcloudUserCredentials.class.isAssignableFrom(credentialType)) {
            return (T) this.credentials;
        }
        return null;
    }

    @Override
    public Set<Credential> getCredentials() {
        if (this.credentials != null) {
            return Set.of(this.credentials);
        }
        return Set.of();
    }

    @Override
    public <T> T getAttribute(String name) {
        return (T) objectMap.get(name);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return objectMap;
    }

    @Override
    public Uni<Boolean> checkPermission(Permission permission) {
        return Uni.createFrom().item(getPermissions().contains(permission));
    }

    /**
     * Returns the nextcloud user of this SecurityContext
     * 
     * @return
     */
    public NextcloudUser getNextcloudUser() {
        return this.user;
    }

    @Override
    public Principal getUserPrincipal() {
        return getPrincipal();
    }

    @Override
    public boolean isUserInRole(String role) {
        return hasRole(role);
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public String getAuthenticationScheme() {
        return SecurityContext.BASIC_AUTH;
    }
}
