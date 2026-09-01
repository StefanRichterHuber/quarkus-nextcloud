package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAPIClientHeaders;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model.DeclarativeSettings;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model.NotificationRequest;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.OCSMessage;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS REST client for the Nextcloud AppAPI-specific endpoints.
 * Provides access to ExApp config, notifications, UI registration, logging,
 * and lifecycle management.
 *
 * @see <a href=
 *      "https://cloud-py-api.github.io/app_api/tech_details/api/">AppAPI REST
 *      API docs</a>
 */
@Path("/")
@RegisterClientHeaders(NextcloudAPIClientHeaders.class)
public interface ExAppApiRestClient {
    /** Base path prefix for AppAPI OCS endpoints. */
    public static final String APP_API_URL_PREFIX = "/ocs/v1.php/apps/app_api";
    /** Base path prefix for AppAPI v1 API endpoints. */
    public static final String API_URL_PREFIX = APP_API_URL_PREFIX + "/api/v1";

    /**
     * Reports this ExApp's initialization progress to the AppAPI.
     *
     * @param progress percentage complete (0–100), or an error payload
     */
    public static record AppInitProgress(int progress, String error) {
        /**
         * Creates a successful progress report.
         *
         * @param progress percentage complete, must be in {@code [0, 100]}
         * @return a progress report with no error
         */
        public static AppInitProgress ok(int progress) {
            return new AppInitProgress(progress, null);
        }

        /**
         * Creates an error progress report.
         *
         * @param progress percentage reached before the error occurred
         * @param error    human-readable error description
         * @return a progress report carrying the error message
         */
        public static AppInitProgress error(int progress, String error) {
            return new AppInitProgress(progress, error);
        }
    }

    /**
     * Represents a single ExApp configuration or preference key-value entry.
     * 
     * @param configKey   key of the config property
     * @param configValue value of the config property
     * @param id          Id of the config property
     * @param appId       Application id of the config property
     * @param sensitive   Is this a sensitive config item
     */
    public static record ExAppConfigValue(
            @JsonProperty("configkey") String configKey,
            @JsonProperty("configvalue") String configValue,
            Integer id,
            @JsonProperty("appid") String appId,
            Integer sensitive) {

        /**
         * Creates a non-sensitive config entry.
         *
         * @param configKey   the configuration key
         * @param configValue the configuration value
         * @return a new config entry with {@code sensitive = 0}
         */
        public static ExAppConfigValue create(String configKey, String configValue) {
            return new ExAppConfigValue(configKey, configValue, null, null, 0);
        }

        /**
         * Creates a sensitive config entry that Nextcloud will store encrypted.
         *
         * @param configKey   the configuration key
         * @param configValue the configuration value
         * @return a new config entry with {@code sensitive = 1}
         */
        public static ExAppConfigValue createSensitiveValue(String configKey, String configValue) {
            return new ExAppConfigValue(configKey, configValue, null, null, 1);
        }
    }

    /**
     * Request body for bulk config-value retrieval by key names.
     * 
     * @param configKeys List of config keys to retrieve
     */
    public static record AppConfigValueRequest(List<String> configKeys) {
        /**
         * Creates a request for the given config keys.
         *
         * @param configKeys one or more config key names to retrieve
         * @return a new request wrapping the given keys
         */
        public static AppConfigValueRequest create(String... configKeys) {
            return new AppConfigValueRequest(List.of(configKeys));
        }

        /**
         * Creates a request for the given config keys.
         *
         * @param configKeys list of config key names to retrieve
         * @return a new request wrapping the given keys
         */
        public static AppConfigValueRequest create(List<String> configKeys) {
            return new AppConfigValueRequest(configKeys);
        }
    }

    /**
     * Summary information about an installed Nextcloud ExApp.
     */
    public static record AppList(String id, String name, String version, boolean enabled,
            String last_check_time,
            boolean system) {
    }

    /**
     * Request body for registering an entry in the Nextcloud Files actions menu.
     */
    public static record RegisterFileActionMenuRequest(String name, String displayName,
            String actionHandler,
            String mime, String icon, int permissions, int order) {
    }

    /**
     * Request body for sending a log entry to the Nextcloud global log.
     */
    public static record LogRequest(Level level, @Nonnull String message) {
        public static enum Level {

            DEBUG(0), INFO(1), WARN(2), ERROR(3), FATAL(4);

            private final int v;

            private Level(int v) {
                this.v = v;
            }

            @JsonValue
            public int getValue() {
                return v;
            }
        }

        /**
         * Creates a log request for the given level and message.
         *
         * @param level   severity level
         * @param message log message text
         * @return a new log request
         */
        public static LogRequest create(Level level, String message) {
            return new LogRequest(level, message);
        }
    }

    /**
     * Request body for registering a top-menu entry in the Nextcloud UI.
     */
    public static record RegisterTopMenuEntryRequest(@Nonnull String name, @Nonnull String displayName,
            String icon,
            int adminRequired) {

    }

    /**
     * Request body for unregistering a previously registered UI element by name.
     */
    public static record UnregisterRequest(@Nonnull String name) {
    }

    /**
     * Request body for setting or removing a Nextcloud initial state value.
     */
    public static record InitialStateRequest(@Nonnull String type, @Nonnull String name,
            @Nonnull String key,
            List<String> value) {
    }

    /**
     * Request body for registering or removing a JavaScript or CSS resource.
     */
    public static record ScriptOrStyleRequest(@Nonnull String type, @Nonnull String name,
            @Nonnull String path,
            String afterAppId) {
    }

    /**
     * Request body for removing a declarative settings form by its form ID.
     */
    public static record RemoveSettingsMenu(String formId) {
    }

    /**
     * Request body for removing a previously registered node event listener.
     */
    public static record RemoveEventListener(
            /** event type: 'node_event' */
            String eventType) {

        /**
         * Creates a request to remove the {@code node_event} listener.
         *
         * @return a remove-listener request for the node event type
         */
        public static RemoveEventListener create() {
            return new RemoveEventListener("node_event");
        }
    }

    /**
     * Request body for registering a node event listener with the AppAPI.
     */
    public static record RegisterEventListener(
            /** event type: 'node_event' */
            String eventType,
            /** Route to the handler */
            String actionHandler,
            /** Optional list of sub types */
            Set<String> eventSubtypes) {

        /**
         * Creates a listener registration for all node event sub-types.
         *
         * @param actionHandler route path of the event handler endpoint
         * @return a new listener registration with an empty sub-type filter
         */
        public static RegisterEventListener create(String actionHandler) {
            return create(actionHandler, Collections.emptySet());
        }

        /**
         * Creates a listener registration filtered to the given sub-types.
         *
         * @param actionHandler route path of the event handler endpoint
         * @param eventSubtypes Nextcloud node event sub-type identifiers to subscribe
         *                      to
         * @return a new listener registration
         */
        public static RegisterEventListener create(String actionHandler,
                String... eventSubtypes) {
            return create(actionHandler, Set.of(eventSubtypes));
        }

        /**
         * Creates a listener registration filtered to the given sub-types.
         *
         * @param actionHandler route path of the event handler endpoint
         * @param eventSubtypes set of Nextcloud node event sub-type identifiers
         * @return a new listener registration
         */
        public static RegisterEventListener create(String actionHandler,
                Set<String> eventSubtypes) {
            return new RegisterEventListener("node_event", actionHandler, eventSubtypes);
        }
    }

    /**
     * @param name           appid:unique:command:name
     * @param description    Description of the command
     * @param hidden         "1/0"
     * @param arguments      Arguments of the command
     * @param options        Options aof the command
     * @param usages         "occ appid:unique:command:name argument_name
     *                       --option_name",
     * @param executeHandler Route for the handler
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/occ_command.html">OCC
     *      Command</a>
     */
    public static record RegisterOCCComandRequest(
            String name,
            String description,
            String hidden,
            List<RegisterOCCComandRequest.Argument> arguments,
            List<RegisterOCCComandRequest.Option> options,
            List<String> usages,
            @JsonProperty("execute_handler") String executeHandler) {

        /**
         * @param name         argument_name
         * @param mode         "required/optional/array"
         * @param description  Description of the argument
         * @param defaultValue Default value
         */
        public static record Argument(
                String name,
                String mode,
                String description,
                @JsonProperty("default") String defaultValue) {
        }

        /**
         * @param name         option_name
         * @param shortcut     "s"
         * @param mode         "required/optional/none/array/negatable"
         * @param description  Description of the option
         * @param defaultValue default value
         */
        public static record Option(
                String name,
                String shortcut,
                String mode,
                String description,
                @JsonProperty("default") String defaultValue) {
        }

    }

    /**
     * Returns the list of Nextcloud user IDs visible to this ExApp.
     *
     * @return an OCS response wrapping the list of user IDs
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/apps/app_api/api/v1/users")
    OCSMessage<List<String>> getUserList();

    /**
     * Reports the initialization progress of this ExApp to the AppAPI server.
     *
     * @param progress progress payload with a percentage value (0–100) and an
     *                 optional error message
     * @return an OCS response confirming receipt of the status
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v1.php/apps/app_api/ex-app/status")
    OCSMessage reportAppInitProgress(AppInitProgress progress);

    /**
     * Sets a single ExApp config or preference value.
     *
     * @param target {@code config} for global config, {@code preference} for
     *               per-user preferences
     * @param value  the key-value pair to store
     * @return an OCS response wrapping the stored config entry
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/appconfig.html">AppConfig
     *      API</a>
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{target}")
    OCSMessage<ExAppConfigValue> setExAppConfigValue(@PathParam("target") String target, ExAppConfigValue value);

    /**
     * Retrieves multiple ExApp config or preference values by key.
     *
     * @param target  {@code config} for global config, {@code preference} for
     *                per-user preferences
     * @param request list of config key names to retrieve
     * @return an OCS response wrapping the matching config entries
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/appconfig.html">AppConfig
     *      API</a>
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{target}/get-values")
    OCSMessage<List<ExAppConfigValue>> getAppConfigValues(@PathParam("target") String target,
            AppConfigValueRequest request);

    /**
     * Deletes ExApp config or preference values.
     *
     * @param target {@code config} for global config, {@code preference} for
     *               per-user preferences
     * @param value  the keys to delete
     * @return an OCS response with the number of deleted entries
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/appconfig.html">AppConfig
     *      API</a>
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{target}")
    OCSMessage<Integer> deleteExAppConfigValue(@PathParam("target") String target, AppConfigValueRequest value);

    /**
     * Returns a list of installed ExApps.
     *
     * @param list {@code enabled} to list only enabled apps, {@code all} for all
     *             apps
     * @return list of installed ExApp summaries
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/exapp.html">ExApp
     *      API</a>
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{list}")
    List<AppList> getAppList(@PathParam("list") String list);

    /**
     * Returns a list of Nextcloud user IDs accessible to this ExApp.
     *
     * @return an OCS response wrapping the user list
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/utils.html">Utils
     *      API</a>
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/users")
    OCSMessage getUsers();

    /**
     * Registers a new entry in the Nextcloud Files actions menu.
     *
     * @param request the menu entry definition
     * @return the HTTP response from the AppAPI
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/fileactionsmenu.html">Files
     *      Actions Menu API</a>
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/files-actions-menu")
    Response registerFileActionsMenu(RegisterFileActionMenuRequest request);

    /**
     * Removes a previously registered entry from the Nextcloud Files actions menu.
     *
     * @param request identifies the menu entry to remove by name
     * @return the HTTP response from the AppAPI
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/fileactionsmenu.html">Files
     *      Actions Menu API</a>
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/files-actions-menu")
    Response unregisterFileActionsMenu(UnregisterRequest request);

    /**
     * Registers a top-menu entry in the Nextcloud navigation bar.
     *
     * @param request the top-menu entry definition
     * @return the HTTP response from the AppAPI
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/topmenu.html">Top
     *      Menu API</a>
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/top-menu")
    Response registerTopMenuEntry(RegisterTopMenuEntryRequest request);

    /**
     * Removes a previously registered top-menu entry.
     *
     * @param request identifies the menu entry to remove by name
     * @return the HTTP response from the AppAPI
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/topmenu.html">Top
     *      Menu API</a>
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/top-menu")
    Response unregisterTopMenuEntry(UnregisterRequest request);

    /**
     * Sends a push notification to a Nextcloud user.
     *
     * @param request the notification payload
     * @return an OCS response confirming delivery
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/notifications.html">Notifications
     *      API</a>
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/notification")
    OCSMessage sendNotification(NotificationRequest request);

    /**
     * Sends a log entry to the Nextcloud global log.
     *
     * @param log the log entry including level and message
     * @return an OCS response confirming the log entry was written
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/logging.html">Logging
     *      API</a>
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/log")
    OCSMessage sendLogEntry(LogRequest log);

    /**
     * Sets an initial state value that Nextcloud injects into a page's
     * JavaScript context.
     *
     * @param req the initial state definition (type, name, key, and value)
     * @return an OCS response confirming the operation
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/initial-state")
    OCSMessage setInitialState(InitialStateRequest req);

    /**
     * Removes a previously set initial state value.
     *
     * @param req identifies the initial state entry to remove
     * @return an OCS response confirming the operation
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/initial-state")
    OCSMessage removeInitialState(InitialStateRequest req);

    /**
     * Registers a JavaScript resource to be loaded by Nextcloud.
     *
     * @param req the script registration request (type, name, path, afterAppId)
     * @return an OCS response confirming the operation
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/script")
    OCSMessage addScript(ScriptOrStyleRequest req);

    /**
     * Registers a CSS stylesheet to be loaded by Nextcloud.
     *
     * @param req the style registration request (type, name, path, afterAppId)
     * @return an OCS response confirming the operation
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/style")
    OCSMessage addStyle(ScriptOrStyleRequest req);

    /**
     * Removes a previously registered JavaScript resource.
     *
     * @param req identifies the script to remove
     * @return an OCS response confirming the operation
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/script")
    OCSMessage removeScript(ScriptOrStyleRequest req);

    /**
     * Removes a previously registered CSS stylesheet.
     *
     * @param req identifies the stylesheet to remove
     * @return an OCS response confirming the operation
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/style")
    OCSMessage removeStyle(ScriptOrStyleRequest req);

    /**
     * Registers a declarative settings form in Nextcloud's admin or personal
     * settings UI.
     *
     * @param settings the settings form definition
     * @return an OCS response confirming the registration
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/settings.html">Settings
     *      API</a>
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/settings")
    OCSMessage registerSettingsMenu(DeclarativeSettings settings);

    /**
     * Removes a previously registered declarative settings form.
     *
     * @param req identifies the settings form to remove by its form ID
     * @return an OCS response confirming the removal
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/tech_details/api/settings.html">Settings
     *      API</a>
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/settings")
    OCSMessage removeSettingsMenu(RemoveSettingsMenu req);

}
