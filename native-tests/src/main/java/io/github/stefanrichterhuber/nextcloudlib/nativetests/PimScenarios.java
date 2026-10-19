package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import biweekly.ICalendar;
import biweekly.component.VEvent;
import ezvcard.VCard;
import ezvcard.parameter.EmailType;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.Calendar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService.CalendarEntry;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService.Addressbook;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * CalDAV / CardDAV scenarios: biweekly and ez-vcard parse and write their
 * formats using scribe registries and resource bundles.
 */
@Path("/native/pim")
public class PimScenarios {
    private static final ZonedDateTime FROM = ZonedDateTime.of(1900, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault());
    private static final ZonedDateTime TO = ZonedDateTime.of(3000, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault());

    @Inject
    NextcloudCalendarService calendarService;

    @Inject
    NextcloudContactService contactService;

    @POST
    @Path("/calendar")
    public void calendar() throws Exception {
        final Calendar personal = calendarService.listCalendars().stream()
                .filter(c -> c.displayname().equalsIgnoreCase("Personal")).findFirst().orElse(null);
        Check.notNull(personal, "Personal calendar not found");

        final String summary = "Native meeting " + UUID.randomUUID();
        final ICalendar ical = new ICalendar();
        final VEvent event = new VEvent();
        event.setSummary(summary);
        event.setDateStart(new Date());
        event.setDateEnd(new Date(System.currentTimeMillis() + 3_600_000L));
        ical.addEvent(event);

        final String uid = calendarService.createCalendarEntry(personal, ical);
        Check.notNull(uid, "UID of created calendar entry");

        List<CalendarEntry> entries = calendarService.fetchCalendar(personal, FROM, TO);
        Check.isTrue(entries.stream().flatMap(ce -> ce.iCal().getEvents().stream())
                .anyMatch(ve -> ve.getSummary().getValue().equals(summary)), "Created event not found");

        calendarService.deleteCalendarEntry(personal.name(), uid);
        entries = calendarService.fetchCalendar(personal, FROM, TO);
        Check.isFalse(entries.stream().flatMap(ce -> ce.iCal().getEvents().stream())
                .anyMatch(ve -> ve.getSummary().getValue().equals(summary)), "Deleted event still found");
    }

    @POST
    @Path("/contacts")
    public void contacts() throws Exception {
        final Addressbook contacts = contactService.listAddressBooks().stream()
                .filter(a -> a.name().equalsIgnoreCase("contacts")).findFirst().orElse(null);
        Check.notNull(contacts, "Address book 'contacts' not found");

        final String mail = UUID.randomUUID() + "@example.com";
        final VCard card = new VCard();
        card.setNickname("Kevin");
        card.addEmail(mail, EmailType.HOME);
        Check.notNull(contactService.createContact(contacts, card), "UID of created contact");

        final VCard created = contactService.fetchContacts(contacts).stream()
                .filter(c -> c.getEmails().stream().anyMatch(m -> m.getValue().equals(mail)))
                .findFirst().orElse(null);
        Check.notNull(created, "Created contact not found");
        Check.equal("Kevin", created.getNickname().getValues().get(0), "Nickname of created contact");

        contactService.deleteContact(contacts, created);
        Check.isFalse(contactService.fetchContacts(contacts).stream()
                .anyMatch(c -> c.getEmails().stream().anyMatch(m -> m.getValue().equals(mail))),
                "Deleted contact still found");
    }
}
