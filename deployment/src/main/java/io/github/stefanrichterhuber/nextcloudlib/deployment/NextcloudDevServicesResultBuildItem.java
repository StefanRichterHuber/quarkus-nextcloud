package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.runtime.LaunchMode;
import io.smallrye.config.SmallRyeConfig;

/**
 * Build-step processor that starts a Nextcloud Testcontainers instance as a
 * Quarkus dev service. Produces a {@link DevServicesResultBuildItem} that
 * injects {@code nextcloud.url}, {@code nextcloud.user}, and
 * {@code nextcloud.password} into the running application.
 *
 * <p>
 * The container is only started when no explicit {@code nextcloud.url} is
 * already configured and dev services are enabled (i.e. not in production
 * mode).
 */
public class NextcloudDevServicesResultBuildItem {
    public static final String NEXTCLOUD_APP_WEBHOOK_LISTENERS = "webhook_listeners";
    public static final String NEXTCLOUD_APP_APP_API = "app_api";

    private static final String HOST_NAME_FOR_DOCKER_CONTAINER = "host.docker.internal";
    private static final String APP_API_DEFAULT_SECRET = "1234567890";
    private static final int SERVICE_PORT = 80;
    private static final String ADMIN_PASSWORD = RandomStringUtils.secure().nextAlphanumeric(12);
    public static final String NEXTCLOUD_URL_PROPERTY = "nextcloud.url";
    public static final String NEXTCLOUD_USER_PROPERTY = "nextcloud.user";
    public static final String NEXTCLOUD_PASSWORD_PROPERTY = "nextcloud.password";

    public static final String NEXTCLOUD_ADMIN_USER_PROPERTY = "nextcloud.admin-user";
    public static final String NEXTCLOUD_ADMIN_PASSWORD_PROPERTY = "nextcloud.admin-password";

    public static final String NEXTCLOUD_TOKEN_PROPERTY = "nextcloud.token";
    public static final String NEXTCLOUD_ADMIN_TOKEN_PROPERTY = "nextcloud.admin-token";

    public static final String NEXTCLOUD_WEBHOOK_HOST_PROPERTY = "nextcloud.webhook.host";

    public static final String NEXTCLOUD_OIDC_IDP_PROPERTY = "quarkus.oidc.auth-server-url";
    public static final String NEXTCLOUD_OIDC_CLIENT_ID_PROPERTY = "quarkus.oidc.client-id"; // QUARKUS_OIDC_CLIENT_ID
    public static final String NEXTCLOUD_OIDC_CLIENT_SECRET_PROPERTY = "quarkus.oidc.credentials.secret"; // QUARKUS_OIDC_CREDENTIALS_SECRET

    private static final String FEATURE_NAME = "nextcloud-dev-service";
    private static final String FEATURE_DESCRIPTION = "Local Nextcloud instance for development and testing purposes. This is only intended to be used in development mode and should not be used in production! The properties "
            + NEXTCLOUD_URL_PROPERTY + ", " + NEXTCLOUD_USER_PROPERTY + " and "
            + NEXTCLOUD_PASSWORD_PROPERTY
            + " are set to allow connecting to this instance using the standard Nextcloud client libraries.";
    private static final String[] OCC_COMMAND_WEBHOOK_CALL = { "background-job:worker", "-v", "-t", "20",
            "OCA\\WebhookListeners\\BackgroundJobs\\WebhookCall" };

    private static final Logger log = Logger.getLogger(NextcloudDevServicesResultBuildItem.class);

    private final ScheduledExecutorService executorService = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor();

    /**
     * Starts the Nextcloud dev-service container and returns a
     * {@link DevServicesResultBuildItem} that exposes connection properties to the
     * application. Returns {@code null} (no-op) when a {@code nextcloud.url} is
     * already present in the config.
     *
     * @param handlers         discovered webhook-event handler descriptors; drives
     *                         whether the {@code webhook_listeners} app is
     *                         installed
     * @param serviceConfig    dev-service configuration
     *                         ({@code nextcloud.dev-services.*})
     * @param exAppBuildConfig ExApp configuration; enables {@code app_api} when
     *                         active
     * @return a build item with injected config properties, or {@code null} when
     *         skipped
     * @throws IOException                   if container exec communication fails
     * @throws UnsupportedOperationException if the container runtime does not
     *                                       support exec
     * @throws InterruptedException          if the thread is interrupted while
     *                                       waiting
     */
    @BuildStep(onlyIfNot = IsProduction.class, onlyIf = DevServicesConfig.Enabled.class)
    public DevServicesResultBuildItem createContainer(
            List<NextcloudEventHandlerBuildItem> handlers,
            NextcloudDevServicesConfig serviceConfig,
            NextcloudExappConfig exAppBuildConfig)
            throws IOException, UnsupportedOperationException, InterruptedException {

        // First check if a nextcloud instance is configured. If it is, no necessity to
        // start the dev service
        final String nextcloudUrl = ConfigProvider.getConfig().getOptionalValue(NEXTCLOUD_URL_PROPERTY, String.class)
                .orElse(null);
        if (nextcloudUrl != null) {
            log.info("Nextcloud url already configured, no need to start Nextcloud dev service");
            return null;
        }

        final String image = serviceConfig.image();
        final String user = serviceConfig.user();
        final String password = serviceConfig.password().orElse(ADMIN_PASSWORD);
        final int logLevel = serviceConfig.logLevel();
        final Set<String> apps = new HashSet<>(serviceConfig.apps().orElse(List.of()));
        final Boolean appApiSupport = serviceConfig.enableExApp() || exAppBuildConfig.enabled();
        final Boolean webhookWorkerEnabled = serviceConfig.enableWebhookWorker();
        final NextcloudContainer container = new NextcloudContainer(image, user, password);

        if (appApiSupport) {
            apps.add(NEXTCLOUD_APP_APP_API);
        }
        if (!appApiSupport && serviceConfig.enableOidc()) {
            apps.add("oidc");
            apps.add("user_oidc");

        }
        // If event handlers are present, enable webhook listeners.
        if (!handlers.isEmpty()) {
            apps.add(NEXTCLOUD_APP_WEBHOOK_LISTENERS);
        }

        container.withApps(apps);
        container.withLogLevel(logLevel);

        container.withReuse(true);
        // Necessary to reach external apps like this one
        container.withExtraHost(HOST_NAME_FOR_DOCKER_CONTAINER, "host-gateway");
        container.start();

        // Prepare configuration to return
        final String newUrl = "http://%s:%d".formatted(container.getHost(),
                container.getMappedPort(SERVICE_PORT));

        Map<String, String> configOverrides = new HashMap<>();

        configOverrides.put(NEXTCLOUD_URL_PROPERTY, newUrl);

        // Admin user and standard user are the same for dev service, so that the dev
        // service can be used for both admin and standard user scenarios. The password
        // is also the same for both.
        configOverrides.put(NEXTCLOUD_USER_PROPERTY, user);
        configOverrides.put(NEXTCLOUD_PASSWORD_PROPERTY, password);

        configOverrides.put(NEXTCLOUD_ADMIN_USER_PROPERTY, user);
        configOverrides.put(NEXTCLOUD_ADMIN_PASSWORD_PROPERTY, password);

        if (apps.contains(NEXTCLOUD_APP_WEBHOOK_LISTENERS)) {
            configOverrides = installWebhookSupport(container, configOverrides, webhookWorkerEnabled);
        }
        if (appApiSupport) {
            configOverrides = installAppApi(container, configOverrides);
        }
        if (!appApiSupport && serviceConfig.enableOidc()) {
            configOverrides = installOIDCProvider(container, configOverrides,
                    serviceConfig.oidcRedirectUrls().orElse(List.of()));
            configOverrides = installOIDC(container, configOverrides);
        }

        String authMode = "'Plain App Password'";
        if (!appApiSupport && serviceConfig.enableOidc()) {
            authMode = "'OIDC Token'";
        } else if (appApiSupport) {
            authMode = "'ExApp API'";
        }

        log.infof(
                "Started nextcloud dev instance at '%s' with apps %s with admin user <%s> and password <%s>. Authentication mode is %s. AppAPI support %s. Background job WebhookCall is %s",
                newUrl,
                apps, user, password, authMode, appApiSupport ? "enabled" : "not enabled",
                webhookWorkerEnabled ? "enabled" : "not enabled");

        return DevServicesResultBuildItem.discovered()
                .feature(FEATURE_NAME)
                .containerId(container.getContainerId())
                .config(configOverrides)
                .description(FEATURE_DESCRIPTION)
                .build();
    }

    /**
     * Determines the web service port of the app
     * 
     * @return Port
     */
    private int determineAppPort() {
        LaunchMode launchMode = LaunchMode.current();
        final int appPort;
        if (launchMode == LaunchMode.TEST) {
            appPort = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        } else if (launchMode == LaunchMode.DEVELOPMENT) {
            appPort = ConfigProvider.getConfig().getValue("quarkus.http.port", Integer.class);
        } else {
            appPort = 8080;
        }
        return appPort;
    }

    /**
     * Creates the config-overwrites for Nextcloud even webhooks
     * 
     * @param container            Nextcloud container to execute occ commands
     * @param configOverrides      Existing config to overwrite
     * @param webhookWorkerEnabled if an additional job for calling background
     *                             webhook worker should be started
     * @return Configuration with additional values for Webehook Event Configuration
     */
    private Map<String, String> installWebhookSupport(NextcloudContainer container, Map<String, String> configOverrides,
            boolean webhookWorkerEnabled) {
        final Map<String, String> result = new HashMap<>(); //
        result.putAll(configOverrides);

        final int appPort = determineAppPort();
        final String webhookHost = "http://host.docker.internal:" + appPort;
        result.put(NEXTCLOUD_WEBHOOK_HOST_PROPERTY, webhookHost);
        // Necessary to ensure app could be reached from docker
        result.put("quarkus.http.host", "0.0.0.0");
        result.put("quarkus.http.test-host", "0.0.0.0");

        // Workaround for "Webhook(3) call failed: Host \"host.docker.internal\"
        // violates local access rules"
        // ./occ config:system:set allow_local_remote_servers --value true --type bool
        container.setConfigValue(null, "allow_local_remote_servers", true).join();

        if (webhookWorkerEnabled) {
            // Start webhook worker in background
            executorService.scheduleAtFixedRate(() -> {
                container.occ(OCC_COMMAND_WEBHOOK_CALL);
            }, 0, 20, java.util.concurrent.TimeUnit.SECONDS);
        }

        return result;
    }

    private Map<String, String> installOIDCProvider(NextcloudContainer container, Map<String, String> configOverrides,
            List<String> redirectURIs) {
        final Map<String, String> result = new HashMap<>(); //
        result.putAll(configOverrides);

        // See https://github.com/H2CK/oidc
        /*
         * Usage:
         * oidc:create [options] [--] <name> <redirect_uris>...
         * 
         * Arguments:
         * name The name of the client
         * redirect_uris An array of redirect uris
         * 
         * 
         * Options:
         * -a, --algorithm=ALGORITHM The signing algorithm to use. Can be ´RS256´ or
         * ´HS256´. [default: "RS256"]
         * -f, --flow=FLOW The flow type to use for authentication. Can be ´code´ or
         * ´code id_token´. [default: "code"]
         * -t, --type=TYPE The type of the client. Can be ´public´ or ´confidential´.
         * [default: "confidential"]
         * --token_type[=TOKEN_TYPE] The type of the access token created for the
         * client. If set to ´jwt´ a RFC9068 conforming access token is generated.
         * --allowed_scopes[=ALLOWED_SCOPES] The allowed scopes for the client. E.g.
         * ´openid profile roles´. If not defined any scope is accepted. [default: ""]
         * --email_regex[=EMAIL_REGEX] The regular expression to select the used email
         * from all email addresses of a user (primary and secondary). If not set always
         * the primary email address will be used. [default: ""]
         * --client_id[=CLIENT_ID] The client id to be used. If not provided the client
         * id will be generated internally. Requirements: printable ASCII except : and
         * length 32-64 [default: ""]
         * --client_secret[=CLIENT_SECRET] The client secret to be used. If not provided
         * the client secret will be generated internally. Requirements: printable ASCII
         * except : and length 32-64 [default: ""]
         * --resource_url[=RESOURCE_URL] The resource URL for this client (RFC 9728).
         * Must be a valid URL with max length 512 characters. [default: ""]
         * -h, --help Display help for the given command. When no command is given
         * display help for the list command
         * -q, --quiet Do not output any message
         * -V, --version Display this application version
         * --ansi|--no-ansi Force (or disable --no-ansi) ANSI output
         * -n, --no-interaction Do not ask any interactive question
         * --no-warnings Skip global warnings, show command output only
         */
        final String clientId = RandomStringUtils.secure().nextAlphanumeric(32);
        final String clientSecret = RandomStringUtils.secure().nextAlphanumeric(32);
        final String url = configOverrides.get(NEXTCLOUD_URL_PROPERTY);
        final String redirectUri = String.format("%s/apps/user_oidc/code", url);
        final String clientName = "devservice";
        result.put(NEXTCLOUD_OIDC_IDP_PROPERTY, url);
        result.put(NEXTCLOUD_OIDC_CLIENT_ID_PROPERTY, clientId);
        result.put(NEXTCLOUD_OIDC_CLIENT_SECRET_PROPERTY, clientSecret);

        List<String> urls = new ArrayList<>();
        urls.add(redirectUri);
        urls.addAll(redirectURIs);

        // TODO resolve ports
        // TOOD all to one list?

        List<String> command = new ArrayList<>();
        command.addAll(
                List.of("oidc:create",
                        "--client_id=" + clientId,
                        "--client_secret=" + clientSecret,
                        "--",
                        clientName));
        command.addAll(urls);
        container.occ(command.stream().toArray(String[]::new));

        // Increase expire time to 1 hour to avoid token expiration during dev service
        // usage
        container.setConfigValue("oidc", "expire_time", 3600).join();
        String urlList = urls.stream().map(u -> "\"" + u + "\"").collect(Collectors.joining(" "));
        log.infof(
                "Installed nextcloud OIDC provider configuration with client id %s and secret %s for callback urls: %s",
                clientId, clientSecret, urlList);
        return result;
    }

    /**
     * Installs the OIDC auth service in the nextcloud container and returns the
     * updated
     * config-overwrites
     * 
     * @param container       Nextcloud container to execute occ commands
     * @param configOverrides Current config to overwrite
     * @return
     */
    private Map<String, String> installOIDC(NextcloudContainer container,
            Map<String, String> configOverrides) {

        final Map<String, String> result = new HashMap<>(); //
        result.putAll(configOverrides);

        // See https://github.com/nextcloud/user_oidc
        // sudo -u www-data php /var/www/nextcloud/occ user_oidc:provider demoprovider
        // --clientid="WBXCa003871" \ --clientsecret="lbXy***********"
        // --discoveryuri="https://accounts.example.com/openid-configuration"
        final String nextcloudUrl = configOverrides.get(NEXTCLOUD_URL_PROPERTY);
        final String discoveryUri = String.format("%s/.well-known/openid-configuration", nextcloudUrl);
        final String clientId = configOverrides.get(NEXTCLOUD_OIDC_CLIENT_ID_PROPERTY);
        final String clientSecret = configOverrides.get(NEXTCLOUD_OIDC_CLIENT_SECRET_PROPERTY);
        final String adminUser = configOverrides.get(NEXTCLOUD_USER_PROPERTY);
        final String adminPassword = configOverrides.get(NEXTCLOUD_PASSWORD_PROPERTY);

        container.setConfigValue(null, "allow_local_remote_servers", true).join();

        container.occ("user_oidc:provider", "devservice", "--clientid=" + clientId,
                "--clientsecret=" + clientSecret,
                "--discoveryuri=" + discoveryUri).join();
        container.setConfigValue("user_oidc", "httpclient.allowselfsigned", true).join();
        container.setConfigValue("user_oidc", "oidc_provider_bearer_validation", true).join();

        // Obtain an actual OIDC access token for the admin user by scripting the
        // authorization code flow against the "oidc" provider app started above, and
        // use it in place of the admin password so consumers authenticate via OIDC.
        final String accessToken = obtainOIDCAccessToken(nextcloudUrl, adminUser, adminPassword, clientId,
                clientSecret);

        result.put(NEXTCLOUD_TOKEN_PROPERTY, accessToken);
        result.put(NEXTCLOUD_ADMIN_TOKEN_PROPERTY, accessToken);

        log.info(
                "Installed nextcloud OIDC login support for admin user and replaced password with access token for dev service");

        return result;
    }

    /**
     * Scripts the OIDC authorization code flow (with PKCE) against the
     * "oidc" provider app running in the dev-service container to obtain an
     * access token for the admin user.
     *
     * <p>
     * The token endpoint of the "oidc" app only accepts the
     * {@code authorization_code} and {@code refresh_token} grant types, so
     * neither resource-owner-password-credentials nor client-credentials can be
     * used here. Since consent is auto-granted (no user settings form) and a
     * session can be established via HTTP Basic auth, the whole flow can be
     * scripted with two plain HTTP requests.
     *
     * @param nextcloudUrl  externally reachable base URL of the Nextcloud
     *                      instance
     * @param adminUser     Nextcloud admin username used to establish the login
     *                      session
     * @param adminPassword Nextcloud admin password used to establish the login
     *                      session
     * @param clientId      client id registered via {@code oidc:create}
     * @param clientSecret  client secret registered via {@code oidc:create}
     * @return the obtained OIDC access token
     */
    private String obtainOIDCAccessToken(String nextcloudUrl, String adminUser, String adminPassword,
            String clientId, String clientSecret) {
        try {
            final String redirectUri = String.format("%s/apps/user_oidc/code", nextcloudUrl);

            final SecureRandom random = new SecureRandom();
            final byte[] verifierBytes = new byte[48];
            random.nextBytes(verifierBytes);
            final String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] challengeBytes = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            final String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

            final String authorizeUrl = nextcloudUrl + "/index.php/apps/oidc/authorize"
                    + "?client_id=" + urlEncode(clientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + urlEncode(redirectUri)
                    + "&scope=" + urlEncode("openid profile email")
                    + "&state=devservice"
                    + "&code_challenge=" + urlEncode(codeChallenge)
                    + "&code_challenge_method=S256";

            final HttpClient httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            final String loginBasicAuth = Base64.getEncoder()
                    .encodeToString((adminUser + ":" + adminPassword).getBytes(StandardCharsets.UTF_8));

            final HttpRequest authorizeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(authorizeUrl))
                    .header("Authorization", "Basic " + loginBasicAuth)
                    .GET()
                    .build();

            final HttpResponse<Void> authorizeResponse = httpClient.send(authorizeRequest,
                    HttpResponse.BodyHandlers.discarding());

            final String location = authorizeResponse.headers().firstValue("Location").orElse(null);
            if (location == null || !location.contains("code=")) {
                throw new IllegalStateException(
                        "Failed to obtain OIDC authorization code, unexpected response status="
                                + authorizeResponse.statusCode() + " location=" + location);
            }

            final String code = extractQueryParam(location, "code");

            final String tokenBasicAuth = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            final String tokenForm = "grant_type=authorization_code"
                    + "&code=" + urlEncode(code)
                    + "&redirect_uri=" + urlEncode(redirectUri)
                    + "&code_verifier=" + urlEncode(codeVerifier);

            final HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(nextcloudUrl + "/index.php/apps/oidc/token"))
                    .header("Authorization", "Basic " + tokenBasicAuth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenForm))
                    .build();

            final HttpResponse<String> tokenResponse = httpClient.send(tokenRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (tokenResponse.statusCode() != 200) {
                throw new IllegalStateException("Failed to obtain OIDC access token, status="
                        + tokenResponse.statusCode() + " body=" + tokenResponse.body());
            }

            final ObjectMapper om = new ObjectMapper();
            final JsonNode tokenJson = om.readTree(tokenResponse.body());
            final String accessToken = tokenJson.path("access_token").asText(null);
            if (accessToken == null) {
                throw new IllegalStateException(
                        "OIDC token response did not contain an access_token: " + tokenResponse.body());
            }
            return accessToken;
        } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to obtain OIDC access token", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Extracts the value of a query parameter from a (possibly absolute) URL.
     *
     * @param url       the URL to inspect
     * @param paramName the name of the query parameter to extract
     * @return the decoded parameter value
     */
    private String extractQueryParam(String url, String paramName) {
        final int queryIndex = url.indexOf('?');
        final String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : url;
        for (String pair : query.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            final String key = pair.substring(0, eq);
            if (key.equals(paramName)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Query parameter '" + paramName + "' not found in url: " + url);
    }

    /**
     * Creates the config-overwrites for AppAPI, registers the AppAPI daemon and
     * this Quarkus App as ExApp
     * 
     * @param container       Nextcloud container to execute occ commands
     * @param configOverrides Existing config to overwrite
     * @return Configuration with additional values for ExApp Configuration
     * @throws IOException
     * @throws InterruptedException
     */
    private Map<String, String> installAppApi(NextcloudContainer container,
            Map<String, String> configOverrides)
            throws IOException, InterruptedException {

        final String daemonName = "local_dev";
        final String appName = ConfigProvider.getConfig().getValue("quarkus.application.name",
                String.class);

        final String appId = appName;
        final String appSecret = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.app-api.secret", String.class)
                .orElse(APP_API_DEFAULT_SECRET);

        final int appPort = determineAppPort();
        final String appPersistentStorage = "tmp/app-storage";
        final String appVersion = ConfigProvider.getConfig().getValue("quarkus.application.version",
                String.class);
        final List<String> appScopes = ConfigProvider.getConfig()
                .getOptionalValues("app.scopes", String.class)
                .orElse(List.of("SYSTEM", "FILES", "FILES_SHARING", "USER_INFO",
                        "USER_STATUS", "NOTIFICATIONS", "WEATHER_STATUS", "TALK",
                        "EVENTS_LISTENER"));
        final boolean appIsSystemApp = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.app-api.system-app", Boolean.class)
                .orElse(false);

        final String nextcloudUrl = configOverrides.get("nextcloud.url");

        // runuser -s /usr/local/bin/php - www-data /var/www/html/occ status

        // Create app-api specific configuration.
        final Map<String, String> appApiConfigOverrides = new HashMap<>(); //
        // Necessary to ensure app cloud be reached from docker Variables usually set by
        // exapp daemon
        appApiConfigOverrides.put("quarkus.http.host", "0.0.0.0");
        appApiConfigOverrides.put("quarkus.http.test-host", "0.0.0.0");
        appApiConfigOverrides.put("aa.version", "1.0.0");
        appApiConfigOverrides.put("app.secret", appSecret);
        appApiConfigOverrides.put("app.id", appName);
        appApiConfigOverrides.put("app.display-name", appName);
        appApiConfigOverrides.put("app.version", appVersion);
        appApiConfigOverrides.put("app.host", HOST_NAME_FOR_DOCKER_CONTAINER);
        appApiConfigOverrides.put("app.port", Integer.toString(appPort));
        appApiConfigOverrides.put("app.protocol", "http");
        appApiConfigOverrides.put("app.persistent-storage", appPersistentStorage);

        appApiConfigOverrides.putAll(configOverrides);

        final ObjectMapper om = new ObjectMapper();
        final Map<String, Object> jsonInfoObj = new HashMap<>();
        jsonInfoObj.put("id", appId);
        jsonInfoObj.put("name", appName);
        jsonInfoObj.put("daemon_config_name", daemonName);
        jsonInfoObj.put("version", appVersion);
        jsonInfoObj.put("secret", appSecret);
        jsonInfoObj.put("port", Integer.toString(appPort));
        jsonInfoObj.put("system_app", appIsSystemApp ? 1 : 0);
        jsonInfoObj.put("scopes", appScopes);

        final String jsonInfo = om.writeValueAsString(jsonInfoObj);

        // This must happen completly async, since nextcloud wants to access the
        // /heartbeat /init and /enabled endpoints which are only available after the
        // full start of the dev service
        container.occ("app_api:daemon:register", daemonName, "Quarkus Dev Services Nextcloud",
                "manual-install", "http", HOST_NAME_FOR_DOCKER_CONTAINER, nextcloudUrl)
                .thenCompose(success -> {
                    if (success) {
                        log.infof("Successfully registered the app_api daemon");
                        return container.occ("app_api:app:register", appName, daemonName, "--json-info", jsonInfo);
                    } else {
                        log.errorf("Failed to register app_api daemon");
                        CompletableFuture<Boolean> r = new CompletableFuture<>();
                        r.complete(success);
                        return r;
                    }
                })
                .thenCompose(success -> {
                    if (success) {
                        log.infof("Successfully registered external app '%s' in nextcloud", appName);
                        return container.occ("app_api:app:enable", appName);
                    } else {
                        log.errorf("Failed to register external app '%s' in nextcloud", appName);
                        CompletableFuture<Boolean> r = new CompletableFuture<>();
                        r.complete(success);
                        return r;
                    }
                })
                .thenAccept(success -> {
                    if (success) {
                        log.infof("Successfully enabled external app '%s' in nextcloud", appName);
                    } else {
                        log.errorf("Failed to enable external app '%s' in nextcloud", appName);
                    }
                });

        return appApiConfigOverrides;
    }

}
