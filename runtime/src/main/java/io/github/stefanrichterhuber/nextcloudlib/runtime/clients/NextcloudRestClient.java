package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FulltextSearchQuery;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.FulltextSearchResult;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.search.Query;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@RegisterClientHeaders(NextcloudAPIClientHeaders.class)
public interface NextcloudRestClient {
    /**
     * Request body for creating a new global system tag.
     * 
     * @param name           User visible name of the tag
     * @param userVisible    Is this tag visible to the user
     * @param userAssignable Can the user assign this tag
     * @param canAssign      Can this tag be assigned
     */
    @RegisterForReflection
    public static record CreateSystemTagRequest(String name, boolean userVisible, boolean userAssignable,
            boolean canAssign) {
    }

    /**
     * Performs webdav search on nextcloud server
     * 
     * @param query XML String containing a MultiStatus response
     * @return
     */
    @SEARCH
    @Produces(MediaType.APPLICATION_XML)
    @Consumes(MediaType.TEXT_XML)
    @Path("remote.php/dav")
    String search(Query query);

    /**
     * Creates a new global system tag with the given configuration
     * 
     * @param req
     */
    @POST
    @Path("remote.php/dav/systemtags")
    Response createNewGlobalSystemTag(CreateSystemTagRequest req);

    /**
     * Performs a full-text search on the nextcloud server
     * 
     * @param request JSON string of {@link FulltextSearchQuery}
     * @return Search result
     */
    @GET
    @Path("index.php/apps/fulltextsearch/v1/remote")
    FulltextSearchResult fulltextsearch(@QueryParam("request") FulltextSearchQuery request);

    /**
     * Gets information about a user by their user ID
     * 
     * @param userId
     * @return
     */
    @GET
    @Path("/ocs/v1.php/cloud/users/{userid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    OCSMessage<NextcloudUser> getUserInfo(@PathParam("userid") String userId);

    /**
     * Request ot add a comment to an object
     * 
     * @param actorType always 'users'
     * @param verb      'comment'
     * @param message   Actual message of the comment
     */
    @RegisterForReflection
    record AddCommentRequest(String actorType, String verb, String message) {
    }

    /**
     * Adds a new comment to an next cloud object
     * 
     * @param type     Object type (e.g. 'files')
     * @param objectId Object id (e.g. file id)
     * @param request  Request
     */
    @POST
    @Path("remote.php/dav/comments/{type}/{objectId}")
    void addComment(@PathParam("type") String type, @PathParam("objectId") String objectId,
            AddCommentRequest request);

    @RegisterForReflection
    public record GetAppPasswordResult(String apppassword) {

    }

    /**
     * Creates a new AppPassword
     * 
     * @param userAgent User Agent (necessary to identify the app password within
     *                  the nextcloud app)
     * @return
     */
    @Path("/ocs/v2.php/core/getapppassword")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<GetAppPasswordResult> getAppPassword(@HeaderParam("User-Agent") String userAgent);

    /**
     * Removes an app password.
     * 
     * @param authorization header: Basic base64_encode(username:app_password)
     * @return
     */
    @DELETE
    @Path("/ocs/v2.php/core/apppassword")
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<Object> deleteAppPassword(@HeaderParam("Authorization") String authorization);

    /**
     * Rotates an app password.
     * 
     * @param authorization header: Basic base64_encode(username:app_password)
     * @return
     */
    @POST
    @Path("/ocs/v2.php/core/apppassword/rotate")
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<GetAppPasswordResult> rotateAppPassword(@HeaderParam("Authorization") String authorization);

    @RegisterForReflection
    record ConfirmAppPasswordRequest(String password) {
    }

    @RegisterForReflection
    record ConfirmAppPasswordResponse(Integer lastLogin) {
    }

    /**
     * Confirms an app password
     * 
     * @param request Request with the app password to configm
     * @return
     */
    @Path("/ocs/v2.php/core/apppassword/confirm")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<ConfirmAppPasswordResponse> confirmAppPassword(ConfirmAppPasswordRequest request);

}
