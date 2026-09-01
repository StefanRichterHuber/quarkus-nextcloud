package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.jboss.logging.Logger;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.model.Multistatus;
import com.github.sardine.report.SardineReport;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.Uid;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NextcloudContactService {
    @Inject
    Logger logger;

    @Inject
    Sardine sardine;

    @Inject
    NextcloudAuthProvider authProvider;

    public record Addressbook(String displayname, String name, String href) {
    }

    /**
     * List all address books of the current user
     * 
     * @throws IOException
     */
    public List<Addressbook> listAddressBooks() throws IOException {
        final String user = authProvider.getUser();
        final String target = String.format("%s/remote.php/dav/addressbooks/users/%s/", authProvider.getServer(),
                user);

        final QName qnameSyncToken = new QName("DAV:", "sync-token", "d");
        final QName qnameDisplayName = new QName("DAV:", "displayname", "d");
        final Set<QName> properties = Set.of( //
                qnameDisplayName, //
                qnameSyncToken //
        );

        final List<DavResource> propfind = this.sardine.propfind(target, 1, properties);
        final List<Addressbook> result = new ArrayList<>(propfind.size());
        for (DavResource r : propfind) {
            final String displayname = r.getDisplayName();
            if (displayname == null || displayname.isBlank()) {
                continue;
            }
            final String href = r.getHref().toString();
            // Remove final /
            String name = href.endsWith("/") ? href.substring(0, href.length() - 1) : href;
            name = name.substring(name.lastIndexOf("/") + 1);
            result.add(new Addressbook(displayname, name, href));
        }
        return result;
    }

    /**
     * Fetches all contacts for the given user and addressbook
     * 
     * @param addressbook Addressbook to fetch
     * @throws IOException
     * @see <a href=
     *      "https://sabre.io/dav/building-a-carddav-client/">https://sabre.io/dav/building-a-carddav-client/</a>
     *      * @see <a href=
     *      "https://github.com/mangstadt/ez-vcard">https://github.com/mangstadt/ez-vcard</a>
     */
    public List<VCard> fetchContacts(@Nonnull Addressbook addressbook) throws IOException {
        if (addressbook == null) {
            return List.of();
        }
        return fetchContacts(addressbook.name());
    }

    /**
     * Fetches all contacts for the given user and addressbook
     * 
     * @param addressbook Name of the addressbook (e.g. 'contacts')
     * @throws IOException
     * @see <a href=
     *      "https://sabre.io/dav/building-a-carddav-client/">https://sabre.io/dav/building-a-carddav-client/</a>
     *      * @see <a href=
     *      "https://github.com/mangstadt/ez-vcard">https://github.com/mangstadt/ez-vcard</a>
     */
    public List<VCard> fetchContacts(@Nonnull String addressbook) throws IOException {
        if (addressbook == null || addressbook.isBlank()) {
            return List.of();
        }
        final String user = authProvider.getUser();
        addressbook = addressbook.replace(" ", "%20");
        final String target = String.format("%s/remote.php/dav/addressbooks/users/%s/%s/", authProvider.getServer(),
                user, addressbook);

        final List<VCard> cards = sardine.report(target, 1, new SardineReport<List<VCard>>() {

            @Override
            public String toXml() throws IOException {
                return "<card:addressbook-query xmlns:d=\"DAV:\" xmlns:card=\"urn:ietf:params:xml:ns:carddav\">\n" //
                        + "    <d:prop>\n" //
                        + "        <d:getetag />\n" //
                        + "        <card:address-data />\n" //
                        + "    </d:prop>\n" //
                        + "</card:addressbook-query>"; //
            }

            @Override
            public Object toJaxb() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public List<VCard> fromMultistatus(Multistatus multistatus) {
                final List<VCard> result = multistatus.getResponse().stream() //
                        .map(r -> r.getPropstat()) //
                        .flatMap(List::stream) //
                        .map(ps -> ps.getProp()) //
                        .filter(p -> p != null) //
                        .map(p -> p.getAny()) //
                        .flatMap(List::stream) //
                        .map(l -> l.getFirstChild()) //
                        .filter(l -> l != null) //
                        .map(n -> n.getNodeValue()) //
                        .filter(n -> n != null && !n.isBlank()) //
                        .map(n -> Ezvcard.parse(n)) //
                        .flatMap(p -> p.all().stream()) //
                        .collect(Collectors.toList());
                return result;
            }
        });

        return cards;
    }

    /**
     * Creates (or replaces) a contact in the given addressbook.
     * <p>
     * If the {@link VCard} has no {@code UID} property a random one is generated
     * and set on the card. CardDAV requires each contact to be stored under its
     * own resource URL ({@code <uid>.vcf}); a PUT against the addressbook
     * collection itself would be rejected with a 409 Conflict.
     *
     * @param addressbook Addressbook to create the contact in
     * @param vcard       Contact to create
     * @return UID of the created contact
     * @throws IOException
     */
    public String createContact(@Nonnull Addressbook addressbook, @Nonnull VCard vcard) throws IOException {
        if (addressbook == null) {
            throw new IllegalArgumentException("Addressbook cannot be null");
        }
        return createContact(addressbook.name(), vcard);
    }

    /**
     * Creates (or replaces) a contact in the given addressbook.
     * <p>
     * If the {@link VCard} has no {@code UID} property a random one is generated
     * and set on the card. CardDAV requires each contact to be stored under its
     * own resource URL ({@code <uid>.vcf}); a PUT against the addressbook
     * collection itself would be rejected with a 409 Conflict.
     *
     * @param addressbook Name of the addressbook (e.g. 'contacts')
     * @param vcard       Contact to create
     * @return UID of the created contact
     * @throws IOException
     */
    public String createContact(@Nonnull String addressbook, @Nonnull VCard vcard) throws IOException {
        if (addressbook == null || addressbook.isBlank()) {
            throw new IllegalArgumentException("Addressbook name cannot be null or blank");
        }
        if (vcard == null) {
            throw new IllegalArgumentException("Contact cannot be null");
        }

        final String uid = contactUid(vcard);
        final String body = Ezvcard.write(vcard).go();

        final String user = authProvider.getUser();
        final String target = String.format("%s/remote.php/dav/addressbooks/users/%s/%s/%s.vcf",
                authProvider.getServer(), user, addressbook.replace(" ", "%20"), uid);

        sardine.put(target, body.getBytes(StandardCharsets.UTF_8), "text/vcard; charset=utf-8");
        return uid;
    }

    /**
     * Deletes the given contact from the given addressbook.
     *
     * @param addressbook Addressbook to delete the contact from
     * @param vcard       Contact to delete (must contain a {@code UID} property)
     * @throws IOException
     */
    public void deleteContact(@Nonnull Addressbook addressbook, @Nonnull VCard vcard) throws IOException {
        if (addressbook == null) {
            throw new IllegalArgumentException("Addressbook cannot be null");
        }
        if (vcard == null || vcard.getUid() == null || vcard.getUid().getValue() == null) {
            throw new IllegalArgumentException("Contact must contain a UID");
        }
        deleteContact(addressbook.name(), vcard.getUid().getValue());
    }

    /**
     * Deletes the contact with the given UID from the given addressbook.
     *
     * @param addressbook Name of the addressbook (e.g. 'contacts')
     * @param uid         UID of the contact to delete
     * @throws IOException
     */
    public void deleteContact(@Nonnull String addressbook, @Nonnull String uid) throws IOException {
        if (addressbook == null || addressbook.isBlank()) {
            throw new IllegalArgumentException("Addressbook name cannot be null or blank");
        }
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("UID cannot be null or blank");
        }
        final String user = authProvider.getUser();
        final String target = String.format("%s/remote.php/dav/addressbooks/users/%s/%s/%s.vcf",
                authProvider.getServer(), user, addressbook.replace(" ", "%20"), uid);

        sardine.delete(target);
    }

    /**
     * Returns the UID of the given contact, generating and setting a random one
     * if the card does not have a {@code UID} property yet.
     */
    private static String contactUid(VCard vcard) {
        if (vcard.getUid() != null && vcard.getUid().getValue() != null && !vcard.getUid().getValue().isBlank()) {
            return vcard.getUid().getValue();
        }
        final Uid uid = Uid.random();
        vcard.setUid(uid);
        return uid.getValue();
    }
}
