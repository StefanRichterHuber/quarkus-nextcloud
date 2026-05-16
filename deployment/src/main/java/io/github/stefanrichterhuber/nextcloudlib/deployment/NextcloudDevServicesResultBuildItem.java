package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.runtime.LaunchMode;

public class NextcloudDevServicesResultBuildItem {
    private static final String APP_API_DEFAULT_SECRET = "1234567890";
    private static final int SERVICE_PORT = 80;
    private static final String ADMIN_PASSWORD = RandomStringUtils.secure().nextAlphanumeric(12);
    public static final String NEXTCLOUD_URL_PROPERTY = "nextcloud.url";
    public static final String NEXTCLOUD_USER_PROPERTY = "nextcloud.user";
    public static final String NEXTCLOUD_PASSWORD_PROPERTY = "nextcloud.password";
    public static final String NEXTCLOUD_WEBHOOK_HOST_PROPERTY = "nextcloud.webhook.host";
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

    @BuildStep(onlyIfNot = IsProduction.class, onlyIf = DevServicesConfig.Enabled.class)
    public DevServicesResultBuildItem createContainer(
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
        final List<String> apps = serviceConfig.apps().orElse(List.of());
        final Boolean appApiSupport = serviceConfig.enableExApp() || exAppBuildConfig.enabled();
        final Boolean webhookWorkerEnabled = serviceConfig.enableWebhookWorker();
        final NextcloudContainer container = new NextcloudContainer(image, user, password);
        container.withApps(apps);
        container.withLogLevel(logLevel);

        if (appApiSupport && !apps.contains("app_api")) {
            container.withApp("app_api");
        }
        container.withReuse(true);
        // Necessary to reach external apps like this one
        container.withExtraHost("host.docker.internal", "host-gateway");
        container.withLogConsumer(of -> {
            log.info(of.getUtf8StringWithoutLineEnding());
        });
        container.start();

        // Prepare configuration to return
        final String newUrl = "http://%s:%d".formatted(container.getHost(),
                container.getMappedPort(SERVICE_PORT));

        Map<String, String> configOverrides = new HashMap<>();

        configOverrides.put(NEXTCLOUD_URL_PROPERTY, newUrl);
        configOverrides.put(NEXTCLOUD_USER_PROPERTY, user);
        configOverrides.put(NEXTCLOUD_PASSWORD_PROPERTY, password);
        if (apps.contains("webhook_listeners")) {
            configOverrides = installWebhookSupport(container, configOverrides, webhookWorkerEnabled);
        }
        if (appApiSupport) {
            configOverrides = installAppApi(container, configOverrides);
        }

        log.infof(
                "Started nextcloud dev instance at <%s> with apps %s with admin user <%s> and password <%s>. AppAPI support %s.",
                newUrl,
                apps, user, password, appApiSupport ? "enabled" : "not enabled");

        return DevServicesResultBuildItem.discovered()
                .feature(FEATURE_NAME)
                .containerId(container.getContainerId())
                .config(configOverrides)
                .description(FEATURE_DESCRIPTION)
                .build();
    }

    private Map<String, String> installWebhookSupport(NextcloudContainer container, Map<String, String> configOverrides,
            boolean webhookWorkerEnabled) {
        final Map<String, String> result = new HashMap<>(); //
        result.putAll(configOverrides);

        LaunchMode launchMode = LaunchMode.current();
        final int appPort;
        if (launchMode == LaunchMode.TEST) {
            appPort = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        } else if (launchMode == LaunchMode.DEVELOPMENT) {
            appPort = ConfigProvider.getConfig().getValue("quarkus.http.port", Integer.class);
        } else {
            appPort = 8080;
        }
        final String webhookHost = "http://host.docker.internal:" + appPort;
        result.put(NEXTCLOUD_WEBHOOK_HOST_PROPERTY, webhookHost);
        // Necessary to ensure app could be reached from docker
        result.put("quarkus.http.host", "0.0.0.0");
        result.put("quarkus.http.test-host", "0.0.0.0");

        // Workaround for "Webhook(3) call failed: Host \"host.docker.internal\"
        // violates local access rules"
        // ./occ config:system:set allow_local_remote_servers --value true --type bool
        container.occ("config:system:set", "allow_local_remote_servers", "--value", "true", "--type", "bool");

        if (webhookWorkerEnabled) {
            // Start webhook worker in background
            executorService.scheduleAtFixedRate(() -> {
                container.occ(OCC_COMMAND_WEBHOOK_CALL);
            }, 0, 20, java.util.concurrent.TimeUnit.SECONDS);
        }

        return result;
    }

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

        LaunchMode launchMode = LaunchMode.current();
        final int appPort;
        if (launchMode == LaunchMode.TEST) {
            appPort = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        } else if (launchMode == LaunchMode.DEVELOPMENT) {
            appPort = ConfigProvider.getConfig().getValue("quarkus.http.port", Integer.class);
        } else {
            appPort = 8080;
        }

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
        appApiConfigOverrides.put("app.host", "0.0.0.0");
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
        // jsonInfoObj.put("system_app", appIsSystemApp ? 1 : 0);
        // jsonInfoObj.put("scopes", appScopes);

        final String jsonInfo = om.writeValueAsString(jsonInfoObj);

        container.occ("app_api:daemon:register", daemonName, "Quarkus Dev Services Nextcloud",
                "manual-install", "http", "host.docker.internal", nextcloudUrl)
                .thenCompose(success -> {
                    if (success) {
                        log.infof("Successfully registred the app_api daemon");
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
                        log.infof("Successfully registred external app '%s' in nextcloud", appName);
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
