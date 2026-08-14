package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import ezvcard.VCard;
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
        List<Addressbook> result = service.listAddressBooks();

        assertNotNull(result);
        assertFalse(result.isEmpty());

        for (Addressbook addressbook : result) {
            List<VCard> card = service.fetchContacts(addressbook);
            assertNotNull(card);
        }
    }
}
