package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Immutable representation of a Nextcloud user as returned by the OCS
 * Provisioning API (<code>GET /ocs/v2.php/cloud/users/{userId}</code>).
 * <p>
 * The record mirrors the metadata Nextcloud exposes for a single account,
 * including profile fields and their visibility scopes. Every profile attribute
 * is paired with a <code>*Scope</code> field describing who may see the value
 * (typically one of <code>v2-private</code>, <code>v2-local</code>,
 * <code>v2-federated</code> or <code>v2-published</code>). Fields that are not
 * set for the user, or that the requesting account is not allowed to read, are
 * returned as {@code null}.
 *
 * @param enabled              whether the account is enabled and allowed to log
 *                             in
 * @param storageLocation      absolute path of the user's data directory on the
 *                             server ({@code null} unless requested by an admin)
 * @param id                   unique, immutable user id (login name)
 * @param firstLoginTimestamp  Unix timestamp (seconds) of the user's first
 *                             login, or {@code -1}/{@code null} if the user never
 *                             logged in
 * @param lastLoginTimestamp   Unix timestamp (seconds) of the user's most recent
 *                             login
 * @param lastLogin            time of the user's most recent login in
 *                             milliseconds since the epoch, or {@code 0} if the
 *                             user never logged in
 * @param backend              name of the user backend that manages this account
 *                             (e.g. {@code Database}, {@code LDAP})
 * @param quota                storage quota and current usage for the user
 * @param manager              user id of this user's line manager, or empty if
 *                             none is configured
 * @param avatarScope          visibility scope of the user's avatar
 * @param email                primary email address of the user
 * @param emailScope           visibility scope of the primary email address
 * @param additionalMail       list of additional email addresses configured for
 *                             the user; entries are raw JSON values as delivered
 *                             by the server
 * @param additionalMailScope  list of visibility scopes matching
 *                             {@code additionalMail}, in the same order
 * @param displayname          display name of the user (Nextcloud
 *                             {@code displayname} property)
 * @param displayDashName      display name of the user as returned under the
 *                             legacy {@code display-name} property
 * @param displaynameScope     visibility scope of the display name
 * @param phone                phone number of the user
 * @param phoneScope           visibility scope of the phone number
 * @param address              postal address of the user
 * @param addressScope         visibility scope of the postal address
 * @param website              website URL of the user
 * @param websiteScope         visibility scope of the website
 * @param twitter              Twitter/X handle of the user
 * @param twitterScope         visibility scope of the Twitter/X handle
 * @param bluesky              Bluesky handle of the user
 * @param blueskyScope         visibility scope of the Bluesky handle
 * @param fediverse            Fediverse handle of the user
 * @param fediverseScope       visibility scope of the Fediverse handle
 * @param organisation         organisation the user belongs to
 * @param organisationScope    visibility scope of the organisation
 * @param role                 job title or role of the user
 * @param roleScope            visibility scope of the role
 * @param headline             short headline shown on the user's profile
 * @param headlineScope        visibility scope of the headline
 * @param biography            free-text biography shown on the user's profile
 * @param biographyScope       visibility scope of the biography
 * @param profileEnabled       whether the user's public profile page is enabled
 *                             ({@code "1"} or {@code "0"})
 * @param profileEnabledScope  visibility scope of the profile-enabled flag
 * @param pronouns             the user's pronouns
 * @param pronounsScope        visibility scope of the pronouns
 * @param groups               ids of the groups the user is a member of
 * @param language             the user's preferred UI language (e.g. {@code en})
 * @param locale               the user's preferred locale (e.g. {@code en_US})
 * @param timezone             the user's configured time zone (e.g.
 *                             {@code Europe/Berlin})
 * @param notify_email         email address used for notifications when it
 *                             differs from the primary {@code email}
 * @param backendCapabilities  capabilities of the user backend for this account,
 *                             indicating which attributes can be modified
 */
@RegisterForReflection
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

    /**
     * Storage quota information for a Nextcloud user. All byte values are
     * reported by the server; a value of {@code -3} for {@code quota} means the
     * user has an unlimited quota, {@code -2} means the quota could not be
     * determined.
     *
     * @param free     number of free bytes still available to the user
     * @param used     number of bytes currently used by the user
     * @param total    total number of bytes available to the user
     *                 ({@code free + used})
     * @param relative used space as a percentage of the total quota, formatted
     *                 as a string (e.g. {@code "12.34"})
     * @param quota    configured quota limit in bytes, or a negative sentinel
     *                 value ({@code -3} = unlimited, {@code -2} = unknown)
     */
    public record Quota(long free, long used, long total, String relative, long quota) {
    }

    /**
     * Describes which user attributes the backend managing this account is able
     * to change.
     *
     * @param setDisplayName whether the display name can be modified through the
     *                       provisioning API
     * @param setPassword    whether the password can be modified through the
     *                       provisioning API
     */
    public record BackendCapabilities(boolean setDisplayName, boolean setPassword) {
    }

}
