package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials.Mode;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @see <a href=
 *      "https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html">https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html</a>
 */
public interface NextcloudLoginFlowRestClient {
    /**
     * 
     * InitiateLoginFlowV2Response
     * 
     * @param login URL the application user needs to access with the browser to
     *              continue the
     *              login flow on the user side
     * @param poll  Polling parameters for the application
     */
    public record InitiateLoginFlowV2Response(String login, Poll poll) {
        /**
         * 
         * Poll parameters
         * 
         * @param token    Token for polling
         * @param endpoint Endpoint for polling
         */
        public record Poll(String token, String endpoint) {
        }
    }

    /**
     * 
     * NextcloudAppCredentials provided by the LoginFlow
     * 
     * @param server      Nextcloud server
     * @param loginName   Nextcloud login name
     * @param appPassword Nextcloud login password
     */
    public record NextcloudAppCredentials(String server, String loginName, String appPassword) {
        public NextcloudUserCredentials toUserCredentials() {
            return new NextcloudUserCredentials(loginName, appPassword, server, Mode.APP_PASSWORD);
        }
    }

    /**
     * Initiate the login flow v2. The user agent paramter equals to the application
     * name shown in the Nextcloud login window
     * 
     * @param userAgent User agent to present (shown to the user in the confirmation
     *                  dialog)
     */
    @POST
    @Path("index.php/login/v2")
    @Produces(MediaType.APPLICATION_JSON)
    InitiateLoginFlowV2Response initiateLoginFlowV2(@HeaderParam("User-Agent") String userAgent);

    /**
     * Polls if the user already performed the login
     * 
     * @param token Poll-token (provided by {@link #initiateLoginFlowV2(String)} )
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("login/v2/poll")
    Response pollLoginFlowV2(@FormParam("token") String token);

    /**
     * Removes an app password.
     * 
     * @param authorization header: Basic base64_encode(username:app_password)
     * @return
     */
    @DELETE
    @Path("ocs/v2.php/core/apppassword")
    @Produces(MediaType.APPLICATION_XML)
    @ClientHeaderParam(name = "OCS-APIREQUEST", value = "true")
    Response deleteAppPassword(@HeaderParam("Authorization") String authorization);

}
