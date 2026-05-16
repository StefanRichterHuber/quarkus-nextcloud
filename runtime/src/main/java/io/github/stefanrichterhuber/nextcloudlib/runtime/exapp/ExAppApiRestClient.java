package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

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

@Path("/")
@RegisterClientHeaders(NextcloudAPIClientHeaders.class)
public interface ExAppApiRestClient {
    public static final String APP_API_URL_PREFIX = "/ocs/v1.php/apps/app_api";
    public static final String API_URL_PREFIX = APP_API_URL_PREFIX + "/api/v1";

    public static record AppInitProgress(int progress, String error) {
        public static AppInitProgress ok(int progress) {
            return new AppInitProgress(progress, null);
        }

        public static AppInitProgress error(int progress, String error) {
            return new AppInitProgress(progress, error);
        }
    }

    public static record ExAppConfigValue(String configKey, String configValue, String sensitive) {
        public static ExAppConfigValue create(String configKey, String configValue) {
            return create(configKey, configValue, false);
        }

        public static ExAppConfigValue create(String configKey, String configValue, boolean sensitive) {
            return new ExAppConfigValue(configKey, configValue, sensitive ? "true" : null);
        }
    }

    public static record AppConfigValueRequest(List<String> configKeys) {
        public static AppConfigValueRequest create(String... configKeys) {
            return new AppConfigValueRequest(List.of(configKeys));
        }

        public static AppConfigValueRequest create(List<String> configKeys) {
            return new AppConfigValueRequest(configKeys);
        }
    }

    public static record AppList(String id, String name, String version, boolean enabled,
            String last_check_time,
            boolean system) {
    }

    public static record RegisterFileActionMenuRequest(String name, String displayName,
            String actionHandler,
            String mime, String icon, int permissions, int order) {
    }

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

        public static LogRequest create(Level level, String message) {
            return new LogRequest(level, message);
        }
    }

    public static record RegisterTopMenuEntryRequest(@Nonnull String name, @Nonnull String displayName,
            String icon,
            int adminRequired) {

    }

    public static record UnregisterRequest(@Nonnull String name) {
    }

    public static record InitialStateRequest(@Nonnull String type, @Nonnull String name,
            @Nonnull String key,
            List<String> value) {
    }

    public static record ScriptOrStyleRequest(@Nonnull String type, @Nonnull String name,
            @Nonnull String path,
            String afterAppId) {
    }

    public static record RemoveSettingsMenu(String formId) {
    }

    public static enum EventSubTypes {
        NodeCreatedEvent, NodeTouchedEvent, NodeWrittenEvent, NodeDeletedEvent, NodeRenamedEvent,
        NodeCopiedEvent
    }

    public static record RemoveEventListener(
            /** event type: 'node_event' */
            String eventType) {

        public static RemoveEventListener create() {
            return new RemoveEventListener("node_event");
        }
    }

    public static record RegisterEventListener(
            /** event type: 'node_event' */
            String eventType,
            /** Route to the handler */
            String actionHandler,
            /** Optional list of sub types */
            Set<EventSubTypes> eventSubtypes) {

        public static RegisterEventListener create(String actionHandler) {
            return create(actionHandler, Collections.emptySet());
        }

        public static RegisterEventListener create(String actionHandler,
                EventSubTypes... eventSubtypes) {
            return create(actionHandler, Set.of(eventSubtypes));
        }

        public static RegisterEventListener create(String actionHandler,
                Set<EventSubTypes> eventSubtypes) {
            return new RegisterEventListener("node_event", actionHandler, eventSubtypes);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/apps/app_api/api/v1/users")
    OCSMessage<List<String>> getUserList();

    /**
     * Returns the initialization status of this app to the AppApi server
     * 
     * @param appId    ID of this app
     * @param progress status (from 0 to 100)
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/ocs/v1.php/apps/app_api/ex-app/status")
    OCSMessage reportAppInitProgress(AppInitProgress progress);

    /**
     * Set ExApp config value
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/appconfig.html#set-app-config-value
     * @param target Either 'config' for global config or 'preference' for user
     *               specific config
     * @param value
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{target}")
    Response setExAppConfigValue(@PathParam("target") String target, ExAppConfigValue value);

    /**
     * Fetches ExApp config values
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/appconfig.html#set-app-config-value
     * @param target  Either 'config' for global config or 'preference' for user
     *                specific config
     * @param request
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{target}/get-values")
    OCSMessage getAppConfigValues(@PathParam("target") String target, AppConfigValueRequest request);

    /**
     * Deletes ExApp config values
     * src/main/java/com/github/StefanRichterHuber/nextcloudaitagging/nextcloud/exapp/RequestAuthFilter.java
     * src/main/java/com/github/StefanRichterHuber/nextcloudaitagging/nextcloud/exapp/AuthRequired.java
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/appconfig.html#set-app-config-value
     * @param target Either 'config' for global config or 'preference' for user
     *               specific config
     * @param value
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{target}")
    void deleteExAppConfigValue(@PathParam("target") String target, AppConfigValueRequest value);

    /**
     * Get list of installed ExApps
     * 
     * @param list either 'enabled' or 'all' to show only enabled or all apps
     * @return
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/exapp.html
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ex-app/{list}")
    List<AppList> getAppList(@PathParam("list") String list);

    /*
     * 
     * Returns a list of user IDs
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/utils.html
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/users")
    OCSMessage getUsers();

    /**
     * Registers a new entry in the File Actions menu
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/fileactionsmenu.html#
     * @param request
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/files-actions-menu")
    Response registerFileActionsMenu(RegisterFileActionMenuRequest request);

    /**
     * Unregisters an entry in the File Actions menu
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/fileactionsmenu.html#
     * @param request
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/files-actions-menu")
    Response unregisterFileActionsMenu(UnregisterRequest request);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/top-menu")
    Response registerTopMenuEntry(RegisterTopMenuEntryRequest request);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/top-menu")
    Response unregisterTopMenuEntry(UnregisterRequest request);

    /**
     * Sends a notification to the user
     * 
     * @param request
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/notifications.html
     * @see https://github.com/nextcloud/server/issues/1706
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/notification")
    OCSMessage sendNotification(NotificationRequest request);

    /**
     * Sends a log entry to the global log file
     * 
     * @param log
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/logging.html
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/log")
    OCSMessage sendLogEntry(LogRequest log);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/initial-state")
    OCSMessage setInitialState(InitialStateRequest req);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/initial-state")
    OCSMessage removeInitialState(InitialStateRequest req);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/script")
    OCSMessage addScript(ScriptOrStyleRequest req);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/style")
    OCSMessage addStyle(ScriptOrStyleRequest req);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/script")
    OCSMessage removeScript(ScriptOrStyleRequest req);

    /**
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/topmenu.html
     * @param req
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/style")
    OCSMessage removeStyle(ScriptOrStyleRequest req);

    /**
     * Register a settings menu
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/settings.html
     * @param settings
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/settings")
    OCSMessage registerSettingsMenu(DeclarativeSettings settings);

    /**
     * Remove a settings menu
     * 
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/settings.html
     * @param req
     * @return
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/ui/settings")
    OCSMessage removeSettingsMenu(RemoveSettingsMenu req);

    /**
     * Registers an event listener
     * 
     * @param req
     * @return
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/events_listener.html
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/events_listener")
    OCSMessage registerEventListener(RegisterEventListener req);

    /**
     * Removes event listener an event listener
     * 
     * @param req
     * @return
     * @see https://cloud-py-api.github.io/app_api/tech_details/api/events_listener.html
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(API_URL_PREFIX + "/events_listener")
    OCSMessage deleteEventListener(RemoveEventListener req);
}
