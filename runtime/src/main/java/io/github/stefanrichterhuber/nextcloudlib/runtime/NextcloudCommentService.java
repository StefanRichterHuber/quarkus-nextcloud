package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.jboss.logging.Logger;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.NextcloudRestClient.AddCommentRequest;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.Comment;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

/**
 * @see <a href=
 *      "https://docs.nextcloud.com/server/stable/developer_manual/client_apis/WebDAV/comments.html">Nextcloud
 *      dev manual</a>
 */
@ApplicationScoped
public class NextcloudCommentService {
    @Inject
    Logger logger;

    @Inject
    Sardine sardine;

    @Inject
    NextcloudAuthProvider authProvider;

    /**
     * Lists all comments of a file
     * 
     * @param file File to list comments for
     * @return List of comments found, never null
     * @throws IOException
     */
    public List<Comment> getCommentsOfFile(NextcloudFile file) throws IOException {
        if (file == null) {
            return List.of();
        }
        String type = "files";
        int resourceId = file.fileId();
        String url = String.format("%s/remote.php/dav/comments/%s/%d", authProvider.getServer(), type, resourceId);

        final Set<QName> props = Set.of(
                new QName("http://owncloud.org/ns", "id", "oc"),
                new QName("http://owncloud.org/ns", "message", "oc"),
                new QName("http://owncloud.org/ns", "actorId", "oc"),
                new QName("http://owncloud.org/ns", "actorDisplayName", "oc"),
                new QName("http://owncloud.org/ns", "creationDateTime", "oc"));

        final List<DavResource> resources = sardine.propfind(url, -1, props);

        final List<Comment> comments = new ArrayList<>();
        for (DavResource resource : resources) {
            if (resource.getCustomProps().get("id") == null || resource.getCustomProps().get("id").isBlank()) {
                continue;
            }
            comments.add(Comment.fromDavResource(resource));
        }

        return comments;
    }

    /**
     * Adds a comment to a file
     * 
     * @param file    File to add comment to
     * @param comment Comment to add
     * @throws IOException
     */
    public void addCommentToFile(NextcloudFile file, String comment) throws IOException {
        if (file == null) {
            throw new IOException("File to add comment to must be given");
        }
        if (comment == null || comment.isBlank()) {
            throw new IOException("Comment to add to file must not be empty or null");
        }
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        final String type = "files";
        final String resourceId = file.fileId().toString();
        try {
            client.addComment(type, resourceId,
                    new AddCommentRequest("users", "comment", comment));
        } catch (WebApplicationException e) {
            throw new IOException(e);
        }
    }
}
