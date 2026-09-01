package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import ezvcard.VCard;
import ezvcard.parameter.EmailType;
import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService.Addressbook;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class NextcloudContactTest {

    @Inject
    NextcloudContactService service;

    @Test
    public void fetchAddressBooks() throws IOException {
        List<Addressbook> addressBook = service.listAddressBooks();

        assertNotNull(addressBook);
        assertFalse(addressBook.isEmpty());

        Addressbook contacts = addressBook.stream().filter(a -> a.name().equalsIgnoreCase("contacts")).findFirst()
                .orElse(null);
        assertNotNull(contacts);

    }

    @Test
    public void createDeleteContact() throws IOException {
        List<Addressbook> addressBook = service.listAddressBooks();

        assertNotNull(addressBook);
        assertFalse(addressBook.isEmpty());

        Addressbook contacts = addressBook.stream().filter(a -> a.name().equalsIgnoreCase("contacts")).findFirst()
                .orElse(null);
        assertNotNull(contacts);

        VCard card = new VCard();
        card.setNickname("Kevin");
        card.addEmail("kevin@example.com", EmailType.HOME);

        String uid = service.createContact(contacts, card);
        assertNotNull(uid);

        List<VCard> cards = service.fetchContacts(contacts);
        VCard createdCard = cards.stream()
                .filter(c -> c.getEmails().stream().anyMatch(m -> m.getValue().equals("kevin@example.com"))).findFirst()
                .orElse(null);
        assertNotNull(createdCard);

    }
}
