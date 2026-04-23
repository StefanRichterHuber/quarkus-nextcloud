package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.security.Principal;
import java.util.Objects;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;

/**
 * Principal based on a NextcloudUser
 */
public class NextcloudSecurityPrincipal implements Principal {
    private final NextcloudUser user;

    public NextcloudSecurityPrincipal(NextcloudUser user) {
        this.user = user;
    }

    @Override
    public String getName() {
        return user.id();
    }

    /**
     * Compares this principal to the specified object. Returns true
     * if the object passed in matches the principal represented by
     * the implementation of this interface.
     *
     * @param another principal to compare with.
     *
     * @return true if the principal passed in is the same as that
     *         encapsulated by this principal, and false otherwise.
     */
    public boolean equals(Object another) {
        if (another instanceof Principal) {
            return Objects.equals(this.getName(), ((Principal) another).getName());
        }
        return false;

    }

    /**
     * Returns a string representation of this principal.
     *
     * @return a string representation of this principal.
     */
    public String toString() {
        return this.user.id();
    }

    /**
     * Returns a hashcode for this principal.
     *
     * @return a hashcode for this principal.
     */
    public int hashCode() {
        return Objects.hash(getName());
    }
}