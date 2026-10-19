package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.TEXT1;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.stream;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import biweekly.ICalendar;
import biweekly.component.VEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.Calendar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.FilterExpression;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.PHPMongoQueryParser.CompiledFilter;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SystemTag;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * Webhook event scenarios: registration of the handlers at startup, Jackson
 * deserialization of the polymorphic payloads, generated invokers, ephemeral
 * credentials and compiled filter expressions.
 */
@Path("/native/events")
public class EventScenarios {
    private static final String EVENT_DIR = "/NATIVE_TESTDIR_EVENTS";

    @Inject
    EventRecorder recorder;

    @Inject
    NextcloudFileService fileService;

    @Inject
    NextcloudSystemTagService tagService;

    @Inject
    NextcloudCalendarService calendarService;

    @Inject
    NextcloudUserService userService;

    @Inject
    NextcloudAuthProvider authProvider;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @FilterExpression("{ \"user.uid\": \"${nextcloud.filter.admin-uid}\", \"time\": { \"$lt\": 3000 } }")
    CompiledFilter filter;

    @Inject
    @ConfigProperty(name = "nextcloud.filter.admin-uid")
    String adminUid;

    @POST
    @Path("/files")
    public void fileEvents() throws Exception {
        final String user = authProvider.getUser();
        final String filename = EVENT_DIR + "/" + UUID.randomUUID() + ".md";
        final Predicate<NextcloudEvent<NextcloudEvent.FileEvent>> isFile = e -> e.event().node().path()
                .equals("/" + user + "/files" + filename);

        fileService.createDirectories(EVENT_DIR);
        fileService.uploadFile(filename, "text/markdown", stream(TEXT1));
        final NextcloudEvent<NextcloudEvent.FileEvent> created = Check.notNull(
                recorder.awaitFileEvent(NextcloudEvent.FileNodeCreatedEvent, isFile),
                "Node created event not received");
        Check.equal(user, created.user().uid(), "User of event");

        // The ephemeral token sent with the event must be usable
        final NextcloudUserCredentials credentials = created.authentication().trigger().toUserCredentials();
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            final String userOfToken = CompletableFuture
                    .supplyAsync(() -> userService.getCurrentUserInfo().id(),
                            new CredentialsAwareRequestScopedExecutor(pool, credentials))
                    .join();
            Check.equal(user, userOfToken, "User of the event token");
        } finally {
            pool.shutdown();
        }

        fileService.deleteFile(filename);
        Check.notNull(recorder.awaitFileEvent(NextcloudEvent.FileNodeDeletedEvent, isFile),
                "Node deleted event not received");
    }

    @POST
    @Path("/system-tags")
    public void systemTagEvents() throws Exception {
        fileService.createDirectories(EVENT_DIR);
        final String filename = EVENT_DIR + "/" + UUID.randomUUID() + ".md";
        fileService.uploadFile(filename, "text/markdown", stream(TEXT1));
        try {
            final NextcloudFile file = fileService.getFile(filename);
            final SystemTag tag = tagService.addSystemTag("native-event-" + UUID.randomUUID(), true, true, true);
            final Predicate<NextcloudEvent<NextcloudEvent.SystemTagEvent>> isTagOfFile = e -> e.event().objectIds()
                    .contains(file.fileId().toString()) && e.event().tagIds().contains(tag.id());

            tagService.addTagToFile(file, tag);
            Check.notNull(recorder.awaitSystemTagEvent(NextcloudEvent.SystemTagAssignedEvent, isTagOfFile),
                    "Tag assigned event not received");

            tagService.removeTagFromFile(file, tag);
            Check.notNull(recorder.awaitSystemTagEvent(NextcloudEvent.SystemTagUnassignedEvent, isTagOfFile),
                    "Tag unassigned event not received");
        } finally {
            fileService.deleteFile(filename);
        }
    }

    @POST
    @Path("/calendar")
    public void calendarEvents() throws Exception {
        final Calendar personal = calendarService.listCalendars().stream()
                .filter(c -> c.displayname().equalsIgnoreCase("Personal")).findFirst().orElse(null);
        Check.notNull(personal, "Personal calendar not found");

        final ICalendar ical = new ICalendar();
        final VEvent event = new VEvent();
        event.setSummary("Native event " + UUID.randomUUID());
        event.setDateStart(new Date());
        ical.addEvent(event);
        final String uid = calendarService.createCalendarEntry(personal, ical);
        try {
            final NextcloudEvent<NextcloudEvent.CalendarObjectEvent> created = Check.notNull(
                    recorder.awaitCalendarEvent(NextcloudEvent.CalendarObjectCreatedEvent,
                            e -> e.event().objectData() != null && e.event().objectData().uri().contains(uid)),
                    "Calendar object created event not received");
            Check.notNull(created.event().calendarData(), "Calendar data of event");
            Check.isTrue("VEVENT".equalsIgnoreCase(created.event().objectData().component()),
                    "Component of calendar object is not a VEVENT");
        } finally {
            calendarService.deleteCalendarEntry(personal.name(), uid);
        }
    }

    /**
     * Object payload for the compiled filter. Classes of the application must be
     * registered for reflection to be converted by Jackson in a native image.
     */
    @RegisterForReflection
    public record FilterTestObject(User user, String id, long time) {
        @RegisterForReflection
        public record User(String name, String uid) {
        }
    }

    @POST
    @Path("/filter")
    public void filter() throws Exception {
        final FilterTestObject match = new FilterTestObject(new FilterTestObject.User("Admin", adminUid), "001", 1000);
        final FilterTestObject noMatch = new FilterTestObject(new FilterTestObject.User("Other", "other"), "002",
                1000);

        Check.isTrue(filter.<FilterTestObject> intoObjectPredicate().test(match), "Object should match");
        Check.isFalse(filter.<FilterTestObject> intoObjectPredicate().test(noMatch), "Object should not match");
        Check.isTrue(filter.intoJSONStringPredicate().test(objectMapper.writeValueAsString(match)),
                "JSON should match");
        Check.isFalse(filter.intoJSONStringPredicate().test(objectMapper.writeValueAsString(noMatch)),
                "JSON should not match");
    }
}
