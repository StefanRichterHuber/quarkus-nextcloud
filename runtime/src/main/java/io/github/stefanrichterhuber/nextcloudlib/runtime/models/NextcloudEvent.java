package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.CalendarObjectEvent.CalendarData;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.CalendarObjectEvent.Shares;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;

/**
 * Represents a Nextcloud event as received from the Nextcloud Webhook API. The
 * event contains the actual event data (as an implementation of the Event
 * interface), the user that triggered the event and the time the event was
 * triggered.
 *
 * @param <T>   the type of the event data, must be an implementation of
 *              {@link Event}
 * @param event the event data specific to the event type
 * @param user  the user that triggered the event
 * @param time  the Unix timestamp (seconds) at which the event was triggered
 */
@RegisterForReflection
public record NextcloudEvent<T extends io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event>(
        T event, User user, long time, Authentication authentication) {
    // --------- One node file events (type FileEvent, property node is set)

    public static final String FileBeforeNodeCreatedEvent = "OCP\\Files\\Events\\Node\\FileBeforeNodeCreatedEvent";
    public static final String FileBeforeNodeTouchedEvent = "OCP\\Files\\Events\\Node\\BeforeNodeTouchedEvent";
    public static final String FileBeforeNodeWrittenEvent = "OCP\\Files\\Events\\Node\\BeforeNodeWrittenEvent";
    public static final String FileBeforeNodeReadEvent = "OCP\\Files\\Events\\Node\\BeforeNodeReadEvent";
    public static final String FileBeforeNodeDeletedEvent = "OCP\\Files\\Events\\Node\\BeforeNodeDeletedEvent";
    public static final String FileNodeCreatedEvent = "OCP\\Files\\Events\\Node\\NodeCreatedEvent";
    public static final String FileNodeTouchedEvent = "OCP\\Files\\Events\\Node\\NodeTouchedEvent";
    public static final String FileNodeWrittenEvent = "OCP\\Files\\Events\\Node\\NodeWrittenEvent";
    public static final String FileNodeDeletedEvent = "OCP\\Files\\Events\\Node\\NodeDeletedEvent";

    // --------- Two node file events (type FileEvent, properties source and target
    // are set)
    public static final String FileNodeCopiedEvent = "OCP\\Files\\Events\\Node\\NodeCopiedEvent";
    public static final String FileNodeRenamedEvent = "OCP\\Files\\Events\\Node\\NodeRenamedEvent";
    public static final String FileNodeRestoredEvent = "OCP\\Files\\Events\\Node\\NodeRestoredEvent";
    public static final String FileBeforeNodeCopiedEvent = "OCP\\Files\\Events\\Node\\BeforeNodeCopiedEvent";
    public static final String FileBeforeNodeRestoredEvent = "OCP\\Files\\Events\\Node\\BeforeNodeRestoredEvent";
    public static final String FileBeforeNodeRenamedEvent = "OCP\\Files\\Events\\Node\\BeforeNodeRenamedEvent";

    // --------- System tag events
    public static final String SystemTagAssignedEvent = "OCP\\SystemTag\\TagAssignedEvent";
    public static final String SystemTagUnassignedEvent = "OCP\\SystemTag\\TagUnassignedEvent";

    // --------- Calendar Object Events
    public static final String CalendarObjectCreatedEvent = "OCP\\Calendar\\Events\\CalendarObjectCreatedEvent";
    public static final String CalendarObjectDeletedEvent = "OCP\\Calendar\\Events\\CalendarObjectDeletedEvent";
    public static final String CalendarObjectMovedToTrashEvent = "OCP\\Calendar\\Events\\CalendarObjectMovedToTrashEvent";
    public static final String CalendarObjectRestoredEvent = "OCP\\Calendar\\Events\\CalendarObjectRestoredEvent";
    public static final String CalendarObjectUpdatedEvent = "OCP\\Calendar\\Events\\CalendarObjectUpdatedEvent";

    // OCP\Calendar\Events\CalendarObjectMovedEvent (contains two calendar data
    // objects, one for source and one for target calendar)
    public static final String CalendarObjectMovedEvent = "OCP\\Calendar\\Events\\CalendarObjectMovedEvent";

    /**
     * The Nextcloud user that triggered the event.
     *
     * @param uid         the internal user identifier
     * @param displayName the human-readable display name of the user
     */
    public record User(String uid, String displayName) {
    }

    /**
     * Temporary auth token of the user that triggered the event.
     * Requested auth tokens are valid for 1 hour after receiving them in the event
     * call request.
     */
    public record Authentication(
            @JsonProperty("user_ids") List<JsonNode> userIds,
            @JsonProperty("trigger") Trigger trigger,
            @JsonProperty("owner") Trigger owner) {
        public record Trigger(String userId, String token, String baseUrl) {
        }
    }

    /**
     * Marker interface for all Nextcloud event payload types.
     * Implementations are discriminated by the {@code class} JSON property,
     * which maps to the fully-qualified Nextcloud PHP event class name.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, // discriminate by name
            include = JsonTypeInfo.As.PROPERTY, // the discriminator is a field in the JSON object
            visible = true, // make the discriminator field visible to Jackson (otherwise it would be
                            // consumed and not passed to the Event implementations)
            property = "class" // maps to @JsonProperty("class") / className()

    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NextcloudEvent.FileEvent.class, names = {
                    NextcloudEvent.FileBeforeNodeCreatedEvent,
                    NextcloudEvent.FileBeforeNodeTouchedEvent,
                    NextcloudEvent.FileBeforeNodeWrittenEvent,
                    NextcloudEvent.FileBeforeNodeReadEvent,
                    NextcloudEvent.FileBeforeNodeDeletedEvent,
                    NextcloudEvent.FileNodeCreatedEvent,
                    NextcloudEvent.FileNodeTouchedEvent,
                    NextcloudEvent.FileNodeWrittenEvent,
                    NextcloudEvent.FileNodeDeletedEvent
            }),
            @JsonSubTypes.Type(value = NextcloudEvent.MoveFileEvent.class, names = {
                    NextcloudEvent.FileNodeCopiedEvent,
                    NextcloudEvent.FileNodeRenamedEvent,
                    NextcloudEvent.FileNodeRestoredEvent,
                    NextcloudEvent.FileBeforeNodeCopiedEvent,
                    NextcloudEvent.FileBeforeNodeRestoredEvent,
                    NextcloudEvent.FileBeforeNodeRenamedEvent
            }),
            @JsonSubTypes.Type(value = NextcloudEvent.SystemTagEvent.class, names = {
                    NextcloudEvent.SystemTagAssignedEvent,
                    NextcloudEvent.SystemTagUnassignedEvent
            }),
            @JsonSubTypes.Type(value = NextcloudEvent.CalendarObjectEvent.class, names = {
                    NextcloudEvent.CalendarObjectCreatedEvent,
                    NextcloudEvent.CalendarObjectDeletedEvent,
                    NextcloudEvent.CalendarObjectMovedToTrashEvent,
                    NextcloudEvent.CalendarObjectRestoredEvent,
                    NextcloudEvent.CalendarObjectUpdatedEvent
            }),
            @JsonSubTypes.Type(value = NextcloudEvent.CalendarMoveEvent.class, names = {
                    NextcloudEvent.CalendarObjectMovedEvent
            })
    })
    public static interface Event {
        @JsonProperty("class")
        String className();
    }

    /**
     * Event payload for single-node file system events such as create, touch,
     * write, read, and delete. The affected file is exposed via {@code node}.
     *
     * @param className the fully-qualified Nextcloud PHP event class name
     * @param node      the affected file-system node, or {@code null} when the node
     *                  is not available for the event type (e.g. before-create
     *                  events)
     */
    public record FileEvent(@JsonProperty("class") String className, @Nullable Node node) implements Event {

        /**
         * A file-system node referenced by a file event.
         *
         * @param id   the internal Nextcloud file identifier, or {@code null} for
         *             nodes that have not been persisted yet
         * @param path the server-relative path of the node (e.g.
         *             {@code /alice/files/report.pdf})
         */
        public record Node(@Nullable Integer id, String path) {
        }
    }

    /**
     * Event payload for two-node file system events such as copy, rename, and
     * restore. Both the original location ({@code source}) and the new location
     * ({@code target}) are provided.
     *
     * @param className the fully-qualified Nextcloud PHP event class name
     * @param source    the original file-system node, or {@code null} for
     *                  before-events
     *                  where the source may not be resolved yet
     * @param target    the destination file-system node, or {@code null} for
     *                  before-events
     *                  where the target may not be resolved yet
     */
    public record MoveFileEvent(@JsonProperty("class") String className,
            @Nullable io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.FileEvent.Node source,
            @Nullable io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.FileEvent.Node target)
            implements Event {
    }

    /**
     * Event payload for system tag assignment and unassignment events.
     *
     * @param className  the fully-qualified Nextcloud PHP event class name
     * @param objectType the type of the tagged object (e.g. {@code files})
     * @param objectIds  the identifiers of the objects the tag was assigned to or
     *                   removed from
     * @param tagIds     the identifiers of the system tags that were assigned or
     *                   removed
     */
    public record SystemTagEvent(@JsonProperty("class") String className, String objectType, List<String> objectIds,
            List<Integer> tagIds) implements Event {
    }

    /**
     * Event payload for calendar object events such as create, update, delete,
     * move to trash, and restore.
     *
     * @param className    the fully-qualified Nextcloud PHP event class name
     * @param calendarId   the internal identifier of the calendar that owns the
     *                     object
     * @param calendarData metadata about the calendar that owns the object
     * @param shares       the sharing state of the calendar at the time of the
     *                     event
     * @param objectData   metadata about the calendar object (e.g. an event or
     *                     task)
     */
    public record CalendarObjectEvent(
            @JsonProperty("class") String className,
            Integer calendarId,
            CalendarData calendarData,
            Shares shares,
            ObjectData objectData) implements Event {

        /**
         * Metadata describing a Nextcloud calendar (DAV collection).
         *
         * @param id                            the internal calendar identifier
         * @param uri                           the DAV URI of the calendar
         * @param getctag                       the CalDAV CTag used to detect
         *                                      collection-level changes
         * @param syncToken                     the WebDAV sync-token for incremental
         *                                      synchronisation
         * @param supportedCalendarComponentSet the set of supported component types
         *                                      (e.g. VEVENT, VTODO)
         * @param scheduleCalendarTransp        the scheduling transparency of the
         *                                      calendar
         */
        public record CalendarData(
                Integer id,
                String uri,
                @JsonProperty("{http://calendarserver.org/ns/}getctag") String getctag,
                @JsonProperty("{http://sabredav.org/ns}sync-token") String syncToken,
                @JsonProperty("{urn:ietf:params:xml:ns:caldav}supported-calendar-component-set") String supportedCalendarComponentSet,
                @JsonProperty("{urn:ietf:params:xml:ns:caldav}schedule-calendar-transp") String scheduleCalendarTransp) {
        }

        /**
         * Represents a single share entry on a calendar.
         *
         * @param href       the DAV href of the sharee
         * @param commonName the human-readable display name of the sharee
         * @param status     the share acceptance status (e.g. 1 = accepted)
         * @param readOnly   {@code true} if the share grants read-only access
         * @param principal  the WebDAV principal URI of the sharee
         * @param groupShare {@code true} if this share targets a group rather than an
         *                   individual user
         */
        public record Shares(
                String href,
                String commonName,
                Integer status,
                Boolean readOnly,
                @JsonProperty("{http://owncloud.org/ns}principal") String principal,
                @JsonProperty("{http://owncloud.org/ns}group-share") Boolean groupShare) {
        }

        /**
         * Metadata describing a single calendar object (e.g. a VEVENT or VTODO entry).
         *
         * @param id             the internal identifier of the calendar object
         * @param uri            the DAV URI of the calendar object
         * @param lastModified   the Unix timestamp (seconds) of the last modification
         * @param etag           the ETag of the calendar object used for conflict
         *                       detection
         * @param calendarId     the internal identifier of the owning calendar
         * @param size           the size of the iCalendar data in bytes
         * @param component      the iCalendar component type (e.g. {@code VEVENT},
         *                       {@code VTODO})
         * @param classification the classification level of the object (e.g. 0 =
         *                       public, 1 = private)
         */
        public record ObjectData(
                Integer id,
                String uri,
                @JsonProperty("lastmodified") Long lastModified,
                @JsonProperty("etag") String etag,
                @JsonProperty("calendarid") Integer calendarId,
                @JsonProperty("size") Integer size,
                @JsonProperty("component") String component,
                @JsonProperty("classification") Integer classification) {
        }
    }

    /**
     * Event payload for a calendar object move between two calendars.
     * Both the source and target calendar metadata and sharing state are provided.
     *
     * @param className          the fully-qualified Nextcloud PHP event class name
     * @param sourceCalendarId   the internal identifier of the calendar the object
     *                           was moved from
     * @param targetCalendarId   the internal identifier of the calendar the object
     *                           was moved to
     * @param targetCalendarData metadata about the destination calendar
     * @param sourceCalendarData metadata about the origin calendar
     * @param sourceShares       the sharing state of the origin calendar at the
     *                           time of the event
     * @param targetShares       the sharing state of the destination calendar at
     *                           the time of the event
     */
    public record CalendarMoveEvent(
            @JsonProperty("class") String className,
            Integer sourceCalendarId,
            Integer targetCalendarId,
            CalendarData targetCalendarData,
            CalendarData sourceCalendarData,
            Shares sourceShares,
            Shares targetShares) implements Event {

    }
}
