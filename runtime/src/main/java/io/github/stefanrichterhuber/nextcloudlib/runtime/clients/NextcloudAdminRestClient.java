package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import java.util.List;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIAdminClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@RegisterClientHeaders(NextcloudAPIAdminClientHeaders.class)
public interface NextcloudAdminRestClient {
    /**
     * Delete a user by their user ID
     * 
     * @param userId The user ID of the user to delete
     * @return
     */
    @DELETE
    @Path("/ocs/v1.php/cloud/users/{userid}")
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<Object> deleteUser(@PathParam("userid") String userId);

    /**
     * Enables a user by their user ID
     * 
     * @param userId The user ID of the user to enable
     * @return
     */
    @PUT
    @Path("/ocs/v1.php/cloud/users/{userid}/enable")
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<Object> enableUser(@PathParam("userid") String userId);

    /**
     * Disables a user by their user ID
     * 
     * @param userId The user ID of the user to disable
     * @return
     */
    @PUT
    @Path("/ocs/v1.php/cloud/users/{userid}/enable")
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<Object> disableUser(@PathParam("userid") String userId);

    public record UserListResponse(List<String> users) {
    }

    /**
     * Lists users on the nextcloud server
     * 
     * @param search Optional Search string to filter users
     * @param limit  Optional Limit of users to return
     * @param offset Optional Offset for pagination
     * @return
     */
    @GET
    @Path("/ocs/v1.php/cloud/users")
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<UserListResponse> listUsers(
            @QueryParam("search") String search,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset);

    @RegisterForReflection
    public record UserInfoResponse(String id) {
    }

    /**
     * Sends a request to create a new user on the nextcloud server.
     * 
     * @param userId      the required username for the new user
     * @param password    the password for the new user, leave empty to send welcome
     *                    mail
     * @param displayName the display name for the new user
     * @param email       the email for the new user, required if password empty
     * @param groups      the groups for the new user
     * @param subadmin    the groups in which the new user is subadmin
     * @param quota       the quota for the new user, leave empty for default
     * @param language    the language for the new user
     * @return
     */
    @POST
    @Path("/ocs/v1.php/cloud/users")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    OCSMessage<UserInfoResponse> createUser(
            @FormParam("userid") String userId,
            @FormParam("password") String password,
            @FormParam("displayName") String displayName,
            @FormParam("email") String email,
            @FormParam("groups") List<String> groups,
            @FormParam("subadmin") List<String> subadmin,
            @FormParam("quota") String quota,
            @FormParam("language") String language

    );
}
