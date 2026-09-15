package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import biweekly.ICalendar;
import biweekly.component.VEvent;
import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.Calendar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.CalendarEntry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudCalendarTest {

    private static final String EVENT_SUMMARY = "Team Meeting";
    @Inject
    NextcloudCalendarService service;

    @Test
    public void testFetchCalendars() throws IOException {
        List<Calendar> calendars = service.listCalendars();
        assertNotNull(calendars);
        assertFalse(calendars.isEmpty());

    }

    @Test
    public void testCreateDeleteCalendarEntry() throws IOException {
        List<Calendar> calendars = service.listCalendars();
        assertNotNull(calendars);
        assertFalse(calendars.isEmpty());

        Calendar personal = calendars.stream().filter(c -> c.displayname().equalsIgnoreCase("Personal")).findFirst()
                .orElse(null);
        assertNotNull(personal);

        ICalendar ical = new ICalendar();

        VEvent event = new VEvent();
        event.setSummary(EVENT_SUMMARY);
        Date start = new Date();
        event.setDateStart(start);
        Date end = new Date();
        end.setYear(28);
        event.setDateEnd(end);
        ical.addEvent(event);

        String uid = service.createCalendarEntry(personal, ical);

        // Fetch calendar entries
        final ZonedDateTime startTime = ZonedDateTime.of(1900, 01, 01, 01, 01, 01, 0, ZoneId.systemDefault());
        final ZonedDateTime endTime = ZonedDateTime.of(3000, 01, 01, 01, 01, 01, 0, ZoneId.systemDefault());
        List<CalendarEntry> cals = service.fetchCalendar(personal, startTime, endTime);
        assertNotNull(cals);
        assertFalse(cals.isEmpty());

        assertTrue(cals.stream().flatMap(ce -> ce.iCal().getEvents().stream())
                .anyMatch(ve -> ve.getSummary().getValue().equals(EVENT_SUMMARY)), "Event created not found");

        service.deleteCalendarEntry(personal.name(), uid);

        cals = service.fetchCalendar(personal, startTime, endTime);
        assertNotNull(cals);
        assertFalse(cals.isEmpty());

        assertFalse(cals.stream().flatMap(ce -> ce.iCal().getEvents().stream())
                .anyMatch(ve -> ve.getSummary().getValue().equals(EVENT_SUMMARY)), "Event should have been deleted ");
    }
}
