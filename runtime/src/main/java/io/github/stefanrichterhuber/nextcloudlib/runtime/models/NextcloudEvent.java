package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.CalendarObjectEvent.CalendarData;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.CalendarObjectEvent.Shares;
import jakarta.annotation.Nullable;

public record NextcloudEvent<T extends io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event>(
                T event, User user, long time) {
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

        public record User(String uid, String displayName) {
        }

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
         * Event for either one file (node is set)
         */
        public record FileEvent(@JsonProperty("class") String className, @Nullable Node node) implements Event {
                public record Node(@Nullable Integer id, String path) {
                }
        }

        /**
         * Event for a move/rename event (both source
         * and target are set)
         */
        public record MoveFileEvent(@JsonProperty("class") String className,
                        @Nullable io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.FileEvent.Node source,
                        @Nullable io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.FileEvent.Node target)
                        implements Event {
        }

        /**
         * Event for system tag assignment/unassignment (objectType, objectIds and
         * tagIds are set)
         */
        public record SystemTagEvent(@JsonProperty("class") String className, String objectType, List<String> objectIds,
                        List<Integer> tagIds) implements Event {
        }

        /**
         * Event for calendar object events (calendarData, shares and objectData are
         * set)
         */
        public record CalendarObjectEvent(
                        @JsonProperty("class") String className,
                        Integer calendarId,
                        CalendarData calendarData,
                        Shares shares,
                        ObjectData objectData) implements Event {

                public record CalendarData(
                                Integer id,
                                String uri,
                                @JsonProperty("{http://calendarserver.org/ns/}getctag") String getctag,
                                @JsonProperty("{http://sabredav.org/ns}sync-token") String syncToken,
                                @JsonProperty("{urn:ietf:params:xml:ns:caldav}supported-calendar-component-set") String supportedCalendarComponentSet,
                                @JsonProperty("{urn:ietf:params:xml:ns:caldav}schedule-calendar-transp") String scheduleCalendarTransp) {
                }

                public record Shares(
                                String href,
                                String commonName,
                                Integer status,
                                Boolean readOnly,
                                @JsonProperty("{http://owncloud.org/ns}principal") String principal,
                                @JsonProperty("{http://owncloud.org/ns}group-share") Boolean groupShare) {
                }

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
         * Event for calendar object move events (calendarData, shares and objectData
         * are
         * set)
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
