package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.jboss.logging.Logger;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient.CreateSystemTagRequest;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SystemTag;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

/**
 * Service for managing system tags on the nextcloud server.
 */
@ApplicationScoped
public class NextcloudSystemTagService {
    @Inject
    Logger logger;

    @Inject
    Sardine sardine;

    @Inject
    NextcloudAuthProvider authProvider;

    /**
     * Creates a new instance of {@link NextcloudRestClient} with the configured
     * server URL and authentication headers
     * 
     * @return Instance of {@link NextcloudRestClient}
     */
    private NextcloudRestClient getApiService() {
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);
        return client;
    }

    /**
     * Adds a new global system tag to the system
     * 
     * @param name           Name of the tag
     * @param userAssignable Is the tag assignable by the user
     * @param canAssign      Can the user assign the tag
     * @param userVisible    Is the tag visible by the user at all
     * @return The new system tag created
     */
    public SystemTag addSystemTag(String name, boolean userAssignable, boolean canAssign, boolean userVisible) {
        final CreateSystemTagRequest req = new CreateSystemTagRequest(name, userVisible, userAssignable, canAssign);
        // Header content-location /remote.php/dav/systemtags/207 -> System tag id 207
        try (final Response response = getApiService().createNewGlobalSystemTag(req)) {
            if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {
                final String contentLocation = response.getHeaderString("content-location");
                if (contentLocation == null || contentLocation.isBlank()) {
                    throw new IllegalStateException("Failed to add system tag " + name
                            + ". Server response missing 'content-location' header");
                }
                final String tagIDString = contentLocation.substring(contentLocation.lastIndexOf("/") + 1);
                final int tagId = Integer.parseInt(tagIDString);
                return new SystemTag(tagId, name, userAssignable, canAssign, userVisible);
            } else {
                throw new IllegalStateException("Failed to add system tag " + name);
            }
        }
    }

    /**
     * List the available system tags
     * 
     * @return {@link List} of {@link SystemTag}s found
     * @throws IOException
     */
    public List<SystemTag> listSystemTags() throws IOException {
        final String target = authProvider.getServer() + "/remote.php/dav/systemtags/";
        final List<DavResource> resources = sardine.propfind(target, 2, Set.of( //
                new QName("http://owncloud.org/ns", "id", "oc"), //
                new QName("http://owncloud.org/ns", "user-assignable", "oc"), //
                new QName("http://owncloud.org/ns", "user-visible", "oc"), //
                new QName("http://owncloud.org/ns", "can-assign", "oc"), //
                new QName("http://owncloud.org/ns", "display-name", "oc") //
        ));

        return resources.stream().filter(r -> !r.getCustomProps().getOrDefault("id", "").equals(""))
                .map(SystemTag::from).collect(Collectors.toList());
    }

    /**
     * List the system tags assigned to a file
     * 
     * @param file file
     * @return {@link List} of tags found
     * @throws IOException
     */
    public List<SystemTag> listSystemTagsOfFile(@Nonnull NextcloudFile file) throws IOException {
        return listSystemTagsOfFile(file.fileId());
    }

    /**
     * List the system tags assigned to a file
     * 
     * @param fileId ID of the file
     * @return {@link List} of tags found
     * @throws IOException
     */
    public List<SystemTag> listSystemTagsOfFile(int fileId) throws IOException {
        final String target = String.format("%s/remote.php/dav/systemtags-relations/files/%d",
                authProvider.getServer(), fileId);
        final List<DavResource> resources = sardine.propfind(target, 2, Set.of( //
                new QName("http://owncloud.org/ns", "id", "oc"), //
                new QName("http://owncloud.org/ns", "user-assignable", "oc"), //
                new QName("http://owncloud.org/ns", "user-visible", "oc"), //
                new QName("http://owncloud.org/ns", "can-assign", "oc"), //
                new QName("http://owncloud.org/ns", "display-name", "oc"), //
                new QName("DAV:", "getetag", "d") //
        ));

        return resources.stream().filter(r -> !r.getCustomProps().getOrDefault("id", "").equals(""))
                .map(SystemTag::from).collect(Collectors.toList());
    }

    /**
     * Adds the given {@link SystemTag} to the given file
     * 
     * @param file file
     * @param tag  {@link SystemTag} to add
     * @throws IOException
     */
    public void addTagToFile(@Nonnull NextcloudFile file, @Nonnull SystemTag tag) throws IOException {
        addTagToFile(file.fileId(), tag.id());
    }

    /**
     * Adds the given {@link SystemTag} to the given file
     * 
     * @param fileId ID of the file
     * @param tag    {@link SystemTag} to add
     * @throws IOException
     */
    public void addTagToFile(int fileId, @Nonnull SystemTag tag) throws IOException {
        addTagToFile(fileId, tag.id());
    }

    /**
     * Adds the system tag with the given id to the given file
     * 
     * @param fileId ID of the file
     * @param tagId  ID of the system tag
     * @throws IOException
     */
    public void addTagToFile(int fileId, int tagId) throws IOException {
        final String target = String.format("%s/remote.php/dav/systemtags-relations/files/%d/%d",
                authProvider.getServer(), fileId, tagId);

        sardine.put(target, new byte[] {});
    }

    /**
     * Removes the system tag from the given file
     * 
     * @param file file
     * @param tag  {@link SystemTag} to remove
     * @throws IOException
     */
    public void removeTagFromFile(@Nonnull NextcloudFile file, @Nonnull SystemTag tag) throws IOException {
        removeTagFromFile(file.fileId(), tag.id());
    }

    /**
     * Removes the system tag from the given file
     * 
     * @param fileId ID of the file
     * @param tag    {@link SystemTag} to remove
     * @throws IOException
     */
    public void removeTagFromFile(int fileId, @Nonnull SystemTag tag) throws IOException {
        removeTagFromFile(fileId, tag.id());
    }

    /**
     * Removes the system tag from the given file
     * 
     * @param fileId ID of the file
     * @param tagId  ID of the system tag to remove
     * @throws IOException
     */
    public void removeTagFromFile(int fileId, int tagId) throws IOException {
        final String target = String.format("%s/remote.php/dav/systemtags-relations/files/%d/%d",
                authProvider.getServer(), fileId, tagId);

        sardine.delete(target);
    }
}
