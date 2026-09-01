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
    private static final String COMMENT_TYPE_FILES = "files";

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
        final String type = COMMENT_TYPE_FILES;
        final int resourceId = file.fileId();
        final String url = String.format("%s/remote.php/dav/comments/%s/%d", authProvider.getServer(), type,
                resourceId);

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
            comments.add(Comment.fromDavResource(resource, type, resourceId));
        }

        return comments;
    }

    /**
     * Deletes a comment from a file
     * 
     * @param comment Comment to delete
     * @throws IOException
     */
    public void deleteComment(Comment comment) throws IOException {
        if (comment == null) {
            throw new NullPointerException("comment must not be null");
        }
        // "/remote.php/dav/comments/files/77/1"
        final String type = comment.type();
        final int fileId = comment.fileId();
        final int commentId = comment.id();
        final String url = String.format("%s/remote.php/dav/comments/%s/%d/%d", authProvider.getServer(), type, fileId,
                commentId);

        sardine.delete(url);

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
            throw new NullPointerException("File to add comment to must be given");
        }
        if (comment == null || comment.isBlank()) {
            throw new NullPointerException("Comment to add to file must not be empty or null");
        }
        final NextcloudRestClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(authProvider.getServer()))
                .followRedirects(true)
                .build(NextcloudRestClient.class);

        final String type = COMMENT_TYPE_FILES;
        final String resourceId = file.fileId().toString();
        try {
            client.addComment(type, resourceId,
                    new AddCommentRequest("users", "comment", comment));
        } catch (WebApplicationException e) {
            throw new IOException(e);
        }
    }
}
