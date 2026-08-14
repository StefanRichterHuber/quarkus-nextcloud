package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.Calendar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.WebDavCalendar;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudCalendarTest {

    @Inject
    NextcloudCalendarService service;

    @Test
    public void testFetchCalendars() throws IOException {
        List<Calendar> calendars = service.listCalendars();
        assertNotNull(calendars);

        for (Calendar c : calendars) {
            List<WebDavCalendar> cals = service.fetchCalendar(c, ZonedDateTime.now().minusDays(10),
                    ZonedDateTime.now());
            assertNotNull(cals);
            // assertFalse(cals.isEmpty()); // Test system calendar is empty
        }
    }
}
