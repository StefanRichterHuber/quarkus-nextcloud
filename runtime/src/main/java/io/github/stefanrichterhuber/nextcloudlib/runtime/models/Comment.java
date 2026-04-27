package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import com.github.sardine.DavResource;

/**
 * Describes a comment on a resource
 * 
 * @param id               ID of the comment
 * @param message          Actual comment message
 * @param actorId          ID of the commenter
 * @param actorDisplayName Display name of the commenter
 * @param creationDateTime Time of the comment creation
 */
public record Comment(Integer id, String message, String actorId,
        String actorDisplayName,
        Date creationDateTime) {

    /**
     * Creates a comment object from a DavResource
     * 
     * @param resource DavResource to parse
     * @return Comment object
     */
    public static Comment fromDavResource(DavResource resource) {
        if (resource == null) {
            return null;
        }
        final Integer id = Integer.parseInt(resource.getCustomProps().get("id"));
        final String message = resource.getCustomProps().get("message");
        final String actorId = resource.getCustomProps().get("actorId");
        final String actorDisplayName = resource.getCustomProps().get("actorDisplayName");
        final String creationDateTimeString = resource.getCustomProps().get("creationDateTime");
        Date creationDateTime = null;
        if (creationDateTimeString != null) {
            final ZonedDateTime creationDateTimeZdt = ZonedDateTime.parse(creationDateTimeString,
                    DateTimeFormatter.RFC_1123_DATE_TIME);
            creationDateTime = Date.from(creationDateTimeZdt.toInstant());
        }
        return new Comment(id, message, actorId, actorDisplayName, creationDateTime);
    }
}