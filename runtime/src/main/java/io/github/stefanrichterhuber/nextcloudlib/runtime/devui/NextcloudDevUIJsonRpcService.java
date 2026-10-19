package io.github.stefanrichterhuber.nextcloudlib.runtime.devui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudDevEventStore.Entry;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudDevEventStore.EventSnapshot;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudEventTracker.CredentialSource;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudEventTracker.Status;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.DefaultNextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudEventInvoker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookRegistrationService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

/**
 * JSON-RPC service backing the Nextcloud Dev UI pages. Only registered in local
 * dev mode (via {@code JsonRPCProvidersBuildItem}).
 *
 * <p>
 * Methods with side effects check whether the application is connected to the
 * Nextcloud dev service started by this extension or to an external instance,
 * and refuse or require confirmation accordingly. The Dev UI disables the
 * corresponding buttons as well, but that is only a convenience.
 * </p>
 */
public class NextcloudDevUIJsonRpcService {
    private static final Logger LOG = Logger.getLogger(NextcloudDevUIJsonRpcService.class);

    /** Property set by the dev service only */
    static final String DEV_SERVICE_CONTAINER_ID_PROPERTY = "nextcloud.dev-services.container-id";
    /** Url of the dev service, set by the dev service only */
    static final String DEV_SERVICE_URL_PROPERTY = "nextcloud.dev-services.url";
    static final String DEV_SERVICE_APPS_PROPERTY = "nextcloud.dev-services.apps";
    static final String DEV_SERVICE_AUTH_MODE_PROPERTY = "nextcloud.dev-services.auth-mode";

    private static final String MASKED = "********";
    /** Name of the temporary app passwords created for replays */
    private static final String REPLAY_APP_PASSWORD_NAME = "Quarkus Dev UI event replay";
    private static final Duration PROBE_CACHE_DURATION = Duration.ofSeconds(5);

    /**
     * How this application is connected to Nextcloud
     */
    public enum InstanceMode {
        /** Connected to the Nextcloud container started by this extension */
        LOCAL_DEV_SERVICE,
        /** Connected to an externally configured Nextcloud */
        EXTERNAL,
        /** No Nextcloud configured or it is not reachable */
        UNAVAILABLE
    }

    @Inject
    DevNextcloudEventTracker eventTracker;

    @Inject
    DevNextcloudExAppStateTracker exAppStateTracker;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final BroadcastProcessor<Map<String, Object>> eventStream = BroadcastProcessor.create();
    private final Consumer<EventSnapshot> storeListener = s -> eventStream.onNext(toUi(s));
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Passwords of Nextcloud users entered in the Dev UI for replays */
    private final Map<String, String> enteredPasswords = new ConcurrentHashMap<>();

    private volatile Map<String, Object> lastProbe;
    private volatile long lastProbeTime;

    @PostConstruct
    void init() {
        eventTracker.addListener(storeListener);
    }

    @PreDestroy
    void destroy() {
        eventTracker.removeListener(storeListener);
        eventStream.onComplete();
    }

    // ------------------------------------------------------------------
    // Instance
    // ------------------------------------------------------------------

    /**
     * @return Url of the configured Nextcloud instance (used by the "Open
     *         Nextcloud" link)
     */
    public String getNextcloudUrl() {
        return config().getOptionalValue("nextcloud.url", String.class).orElse("");
    }

    /**
     * @return Short description of the connected instance for the Dev UI card
     */
    @Blocking
    public String getModeText() {
        final Map<String, Object> info = getInstanceInfo();
        return switch ((InstanceMode) info.get("mode")) {
            case LOCAL_DEV_SERVICE -> "Local dev service";
            case EXTERNAL -> "External: " + info.get("host");
            case UNAVAILABLE -> info.get("url") == null ? "Not configured" : "Not reachable";
        };
    }

    /**
     * @return {@code true} if connected to the dev service started by this
     *         extension. Does not check reachability.
     */
    static boolean isLocalDevService() {
        final Config config = config();
        final Optional<String> containerId = config.getOptionalValue(DEV_SERVICE_CONTAINER_ID_PROPERTY, String.class);
        final Optional<String> devUrl = config.getOptionalValue(DEV_SERVICE_URL_PROPERTY, String.class);
        final Optional<String> url = config.getOptionalValue("nextcloud.url", String.class);
        return containerId.isPresent() && devUrl.isPresent() && devUrl.equals(url);
    }

    /**
     * Information about the connected Nextcloud instance. Credentials are only
     * included for the local dev service.
     *
     * @return Instance information
     */
    @Blocking
    public Map<String, Object> getInstanceInfo() {
        final Config config = config();
        final Optional<String> url = config.getOptionalValue("nextcloud.url", String.class);
        final boolean local = isLocalDevService();
        final Map<String, Object> probe = url.map(this::probe).orElse(Map.of("reachable", false));
        final boolean reachable = Boolean.TRUE.equals(probe.get("reachable"));

        final InstanceMode mode = !reachable ? InstanceMode.UNAVAILABLE
                : local ? InstanceMode.LOCAL_DEV_SERVICE : InstanceMode.EXTERNAL;

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("localDevService", local);
        result.put("url", url.orElse(null));
        result.put("host", url.map(u -> {
            try {
                return URI.create(u).getHost();
            } catch (IllegalArgumentException e) {
                return u;
            }
        }).orElse(null));
        result.put("status", probe);
        result.put("user", config.getOptionalValue("nextcloud.user", String.class).orElse(null));
        result.put("adminUser", config.getOptionalValue("nextcloud.admin-user", String.class).orElse(null));
        final Optional<String> password = config.getOptionalValue("nextcloud.password", String.class);
        final Optional<String> adminPassword = config.getOptionalValue("nextcloud.admin-password", String.class);
        // Never hand out credentials of an external instance
        result.put("password", local ? password.orElse(null) : null);
        result.put("adminPassword", local ? adminPassword.orElse(null) : null);
        result.put("passwordConfigured", password.isPresent() || adminPassword.isPresent());
        result.put("authMode", authMode(config));
        result.put("apps", local
                ? config.getOptionalValues(DEV_SERVICE_APPS_PROPERTY, String.class).orElse(List.of())
                : List.of());
        result.put("containerId", local
                ? config.getOptionalValue(DEV_SERVICE_CONTAINER_ID_PROPERTY, String.class).orElse(null)
                : null);
        result.put("exAppEnabled", exAppEnabled(config));
        return result;
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /**
     * @return All captured events, newest first
     */
    public List<Map<String, Object>> getEvents() {
        return eventTracker.list().stream().map(this::toUi).toList();
    }

    /**
     * @return Stream of new or updated events
     */
    public Multi<Map<String, Object>> streamEvents() {
        return eventStream;
    }

    /**
     * Removes all captured events
     *
     * @return always {@code true}
     */
    public boolean clearEvents() {
        eventTracker.clear();
        return true;
    }

    /**
     * Replays a captured event.
     *
     * <p>
     * Credentials: the original webhook token is used if it was not deleted yet.
     * Otherwise, for handlers requesting a token, a temporary app password is
     * created for the user that triggered the original event. Like the ephemeral
     * token of a webhook event, it is deleted after the handler finished (if
     * {@code nextcloud.webhook.cleanup-auth-tokens} is enabled). The original
     * token is never deleted by a replay.
     * </p>
     *
     * <p>
     * The password of the user is taken from the configuration if the user is the
     * configured {@code nextcloud.user} or {@code nextcloud.admin-user}, else from
     * the passwords entered in the Dev UI. If none is known (or it is wrong), the
     * result contains {@code passwordRequired=true} and the {@code user}, so the
     * Dev UI can ask for it and call this method again with {@code password}.
     * </p>
     *
     * @param id         Id of the captured event
     * @param editedJson Optionally modified event JSON, {@code null} or empty to
     *                   replay the original
     * @param confirmed  Must be {@code true} when connected to an external
     *                   instance, since handlers may modify data there
     * @param password   Optional password of the user of the original event,
     *                   entered in the Dev UI. Kept in memory for further replays.
     * @return Result with {@code success}, {@code message} and the id of the new
     *         event entry, or {@code passwordRequired}
     */
    @Blocking
    public Map<String, Object> replay(String id, String editedJson, boolean confirmed, String password) {
        final Optional<Entry> original = eventTracker.get(id);
        if (original.isEmpty()) {
            return failure("Event " + id + " not found (maybe dropped from the buffer)");
        }
        final Entry entry = original.get();
        final boolean local = isLocalDevService();
        if (!local && !confirmed) {
            return failure("Replay against an external Nextcloud instance requires confirmation");
        }

        final InjectableInstance<NextcloudEventDispatcher> dispatcherInstance = Arc.container()
                .select(NextcloudEventDispatcher.class);
        final InjectableInstance<NextcloudWebhookRegistrationService> registration = Arc.container()
                .select(NextcloudWebhookRegistrationService.class);
        if (!dispatcherInstance.isResolvable() || !registration.isResolvable()) {
            return failure("No Nextcloud event handlers registered");
        }
        if (!(dispatcherInstance.get() instanceof DefaultNextcloudEventDispatcher dispatcher)) {
            return failure("Replay is only supported with the default NextcloudEventDispatcher");
        }
        final NextcloudEventInvoker invoker = registration.get().getEventHandlerById(entry.handlerId);
        if (invoker == null) {
            return failure("No handler registered for id " + entry.handlerId);
        }

        // Build the event
        final String json;
        final NextcloudEvent<Event> event;
        try {
            json = editedJson != null && !editedJson.isBlank() ? unmask(editedJson, entry.rawJson) : entry.rawJson;
            final ObjectMapper appMapper = Arc.container().select(ObjectMapper.class).get();
            event = appMapper.readValue(json, new TypeReference<NextcloudEvent<Event>>() {
            });
        } catch (Exception e) {
            return failure("Invalid event JSON: " + e.getMessage());
        }

        // Select credentials
        final NextcloudUserCredentials credentials;
        final CredentialSource source;
        if (entry.credentials != null && !entry.tokenRevoked) {
            credentials = entry.credentials;
            source = CredentialSource.ORIGINAL;
        } else if (invoker.requestAuthToken()) {
            final String user = entry.credentials != null ? entry.credentials.loginName()
                    : event.user() != null ? event.user().uid() : null;
            if (user == null) {
                return failure("The event contains no user to create a temporary app password for");
            }
            final String server = entry.credentials != null ? entry.credentials.server() : getNextcloudUrl();
            if (password != null && !password.isEmpty()) {
                enteredPasswords.put(user, password);
            }
            final Optional<String> userPassword = passwordOf(user);
            if (userPassword.isEmpty()) {
                return passwordRequired(user, "The original auth token was deleted. Enter the password of user '"
                        + user + "' to create a temporary app password for the replay.");
            }
            try {
                credentials = createTemporaryAppPassword(user, userPassword.get(), server);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to create temporary app password for user %s", user);
                return failure("Failed to create a temporary app password for user '" + user + "': "
                        + e.getMessage());
            }
            if (credentials == null) {
                // Most likely a wrong password: forget it and ask again
                enteredPasswords.remove(user);
                return passwordRequired(user, "Could not create a temporary app password for user '" + user
                        + "'. Is the password correct?");
            }
            source = CredentialSource.TEMPORARY;
        } else {
            credentials = null;
            source = CredentialSource.NONE;
        }

        final String newId = eventTracker.capture(entry.handlerId, json,
                event.event() != null ? event.event().className() : entry.eventClass, credentials, entry.id, source);
        // Only the temporary app password created for this replay is cleaned up (like
        // the ephemeral token of a webhook event). The original token belongs to the
        // original event and is cleaned up by its dispatch.
        final boolean cleanupToken = source == CredentialSource.TEMPORARY;
        final CompletableFuture<Void> result = dispatcher.dispatchTracked(entry.handlerId, event, credentials,
                cleanupToken, cleanupToken ? () -> eventTracker.markTokenRevoked(newId) : null);
        if (result == null) {
            eventTracker.setStatus(newId, Status.NO_HANDLER, "No handler registered for id "
                    + entry.handlerId);
        } else {
            result.whenComplete((v, err) -> eventTracker.complete(newId,
                    err instanceof CompletionException && err.getCause() != null ? err.getCause() : err));
        }
        LOG.infof("Replayed Nextcloud event %s as %s with credentials %s", entry.id, newId, source);

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("id", newId);
        response.put("credentialSource", source);
        response.put("message", "Replayed event " + entry.id + " (credentials: " + source + ")");
        return response;
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /**
     * @return Known event handlers with their registered webhooks
     */
    public Map<String, Object> getHandlers() {
        final InjectableInstance<NextcloudWebhookRegistrationService> registration = Arc.container()
                .select(NextcloudWebhookRegistrationService.class);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventsEnabled", registration.isResolvable());
        result.put("handlers", registration.isResolvable() ? registration.get().listHandlers() : List.of());
        return result;
    }

    /**
     * Deletes and re-registers all webhooks of this application in Nextcloud
     *
     * @param confirmed Must be {@code true} when connected to an external instance
     * @return Result with {@code success} and {@code message}
     */
    @Blocking
    public Map<String, Object> reregisterWebhooks(boolean confirmed) {
        if (!isLocalDevService() && !confirmed) {
            return failure("Re-registering webhooks on an external Nextcloud instance requires confirmation");
        }
        final InjectableInstance<NextcloudWebhookRegistrationService> registration = Arc.container()
                .select(NextcloudWebhookRegistrationService.class);
        if (!registration.isResolvable()) {
            return failure("No Nextcloud event handlers registered");
        }
        registration.get().registerWebhooks(true);
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Webhooks re-registered");
        return response;
    }

    // ------------------------------------------------------------------
    // ExApp
    // ------------------------------------------------------------------

    /**
     * @return Last observed ExApp lifecycle state
     */
    public Map<String, Object> getExAppStatus() {
        final Config config = config();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("exAppEnabled", exAppEnabled(config));
        result.put("localDevService", isLocalDevService());
        result.put("appId", config.getOptionalValue("app.id", String.class).orElse(null));
        result.put("appVersion", config.getOptionalValue("app.version", String.class).orElse(null));
        result.put("state", exAppStateTracker.snapshot());
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Config config() {
        return ConfigProvider.getConfig();
    }

    /**
     * Returns the password of the given user: as entered in the Dev UI, or from the
     * configuration if it is the configured user or admin user.
     */
    private Optional<String> passwordOf(String user) {
        final String entered = enteredPasswords.get(user);
        if (entered != null) {
            return Optional.of(entered);
        }
        final Config config = config();
        if (user.equals(config.getOptionalValue("nextcloud.user", String.class).orElse(null))) {
            return config.getOptionalValue("nextcloud.password", String.class);
        }
        if (user.equals(config.getOptionalValue("nextcloud.admin-user", String.class).orElse(null))) {
            return config.getOptionalValue("nextcloud.admin-password", String.class);
        }
        return Optional.empty();
    }

    /**
     * Creates a temporary app password for the given user, to replay an event whose
     * ephemeral token was already deleted. The app password is deleted after the
     * replay by the regular token cleanup of the dispatcher.
     *
     * @param user     Login name of the user
     * @param password Password of the user
     * @param server   Nextcloud server url
     * @return Temporary credentials or null if Nextcloud refused to create one
     *         (e.g. wrong password)
     * @throws Exception if the call failed
     */
    private static NextcloudUserCredentials createTemporaryAppPassword(String user, String password, String server)
            throws Exception {
        final NextcloudUserCredentials login = new NextcloudUserCredentials(user, password, server,
                NextcloudUserCredentials.Mode.APP_PASSWORD);
        final ScheduledExecutorService executorService = Arc.container().select(ScheduledExecutorService.class)
                .get();
        final NextcloudUserService userService = Arc.container().select(NextcloudUserService.class).get();
        // Run as the user, so the app password is created for it
        return CompletableFuture
                .supplyAsync(() -> userService.getAppPassword(REPLAY_APP_PASSWORD_NAME),
                        new CredentialsAwareRequestScopedExecutor(executorService, login))
                .get(30, TimeUnit.SECONDS);
    }

    private static Map<String, Object> passwordRequired(String user, String message) {
        final Map<String, Object> response = failure(message);
        response.put("passwordRequired", true);
        response.put("user", user);
        return response;
    }

    private static boolean exAppEnabled(Config config) {
        return config.getOptionalValue("nextcloud.exapp.enabled", Boolean.class).orElse(false);
    }

    private static String authMode(Config config) {
        if (exAppEnabled(config)) {
            return "ExApp API";
        }
        return config.getOptionalValue(DEV_SERVICE_AUTH_MODE_PROPERTY, String.class).orElse("App password");
    }

    private static Map<String, Object> failure(String message) {
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    /**
     * Checks if the Nextcloud instance is reachable using its {@code status.php}.
     * The result is cached for a few seconds.
     */
    private Map<String, Object> probe(String url) {
        final long now = System.currentTimeMillis();
        final Map<String, Object> cached = lastProbe;
        if (cached != null && Objects.equals(cached.get("url"), url)
                && now - lastProbeTime < PROBE_CACHE_DURATION.toMillis()) {
            return cached;
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        try {
            final String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            final HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/status.php"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                final JsonNode status = jsonMapper.readTree(response.body());
                result.put("reachable", true);
                result.put("version", status.path("versionstring").asText(null));
                result.put("installed", status.path("installed").asBoolean(false));
                result.put("maintenance", status.path("maintenance").asBoolean(false));
            } else {
                result.put("reachable", false);
                result.put("error", "HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.put("reachable", false);
            result.put("error", e.getMessage());
        } catch (Exception e) {
            result.put("reachable", false);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        lastProbe = result;
        lastProbeTime = now;
        return result;
    }

    /**
     * Converts an event into the representation for the Dev UI. Ephemeral auth
     * tokens in the JSON are masked.
     */
    private Map<String, Object> toUi(EventSnapshot s) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", s.id());
        result.put("timestamp", s.timestamp());
        result.put("handlerId", s.handlerId());
        result.put("eventClass", s.eventClass());
        result.put("triggerUser", s.triggerUser());
        result.put("json", mask(s.rawJson()));
        result.put("status", s.status());
        result.put("error", s.error());
        result.put("durationMs", s.durationMs());
        result.put("tokenRevoked", s.tokenRevoked());
        result.put("replayOf", s.replayOf());
        result.put("credentialSource", s.credentialSource());
        return result;
    }

    private String mask(String rawJson) {
        try {
            final JsonNode node = jsonMapper.readTree(rawJson);
            replaceTokens(node, MASKED, null);
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            // Unparsable body: show as is
            return rawJson;
        }
    }

    /**
     * Restores the original tokens in an edited JSON if the user left the masked
     * placeholder untouched.
     */
    private String unmask(String editedJson, String rawJson) throws Exception {
        final JsonNode edited = jsonMapper.readTree(editedJson);
        JsonNode original;
        try {
            original = jsonMapper.readTree(rawJson);
        } catch (Exception e) {
            original = null;
        }
        replaceTokens(edited, MASKED, original);
        return jsonMapper.writeValueAsString(edited);
    }

    /**
     * Replaces the tokens of {@code authentication.trigger} and
     * {@code authentication.owner}. If {@code original} is null, the tokens are
     * replaced by {@code value}. Otherwise tokens equal to {@code value} are
     * replaced by the original ones.
     */
    private static void replaceTokens(JsonNode root, String value, JsonNode original) {
        final JsonNode authentication = root.path("authentication");
        for (String key : List.of("trigger", "owner")) {
            if (authentication.path(key) instanceof ObjectNode tokenHolder && tokenHolder.hasNonNull("token")) {
                if (original == null) {
                    tokenHolder.put("token", value);
                } else if (value.equals(tokenHolder.path("token").asText())) {
                    final JsonNode originalToken = original.path("authentication").path(key).path("token");
                    if (originalToken.isTextual()) {
                        tokenHolder.put("token", originalToken.asText());
                    }
                }
            }
        }
    }
}
