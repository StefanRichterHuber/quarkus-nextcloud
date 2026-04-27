package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import com.github.sardine.DavResource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SystemTag(int id, String displayName, boolean userAssignable, boolean canAssign,
        boolean userVisible) {

    /**
     * Creates a {@link SystemTag} from a {@link DavResource} with the necessary
     * fields fetched
     * 
     * @param resource
     * @return
     */
    public static SystemTag from(DavResource resource) {
        if (resource != null) {
            final int id = Integer.parseInt(resource.getCustomProps().get("id"));
            final String displayName = resource.getCustomProps().get("display-name");
            final boolean userVisible = "true".equalsIgnoreCase(resource.getCustomProps().get("user-visible"));
            final boolean userAssignable = "true"
                    .equalsIgnoreCase(resource.getCustomProps().get("user-assignable"));
            final boolean canAssign = "true".equalsIgnoreCase(resource.getCustomProps().get("can-assign"));
            return new SystemTag(id, displayName, userAssignable, canAssign, userVisible);
        }
        return null;
    }
}