package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Nextcloud user record
 */
public record NextcloudUser(
        boolean enabled,
        String storageLocation,
        String id,
        Long firstLoginTimestamp,
        Long lastLoginTimestamp,
        Long lastLogin,
        String backend,
        Quota quota,
        String manager,
        String avatarScope,
        String email,
        String emailScope,
        @JsonProperty("additional_mail") List<JsonNode> additionalMail,
        @JsonProperty("additional_mailScope") List<JsonNode> additionalMailScope,
        @JsonProperty("displayname") String displayname,
        @JsonProperty("display-name") String displayDashName,
        @JsonProperty("displaynameScope") String displaynameScope,
        String phone,
        String phoneScope,
        String address,
        String addressScope,
        String website,
        String websiteScope,
        String twitter,
        String twitterScope,
        String bluesky,
        String blueskyScope,
        String fediverse,
        String fediverseScope,
        String organisation,
        String organisationScope,
        String role,
        String roleScope,
        String headline,
        String headlineScope,
        String biography,
        String biographyScope,
        @JsonProperty("profile_enabled") String profileEnabled,
        @JsonProperty("profile_enabledScope") String profileEnabledScope,
        String pronouns,
        String pronounsScope,
        List<String> groups,
        String language,
        String locale,
        String timezone,
        String notify_email,
        BackendCapabilities backendCapabilities) {

    public record Quota(long free, long used, long total, String relative, long quota) {
    }

    public record BackendCapabilities(boolean setDisplayName, boolean setPassword) {
    }

}
