package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;

public class NextcloudDevServicesResultBuildItem {
    private static final String APP_API_DEFAULT_SECRET = "1234567890";
    private static final int SERVICE_PORT = 80;
    private static final String SERVICE_IMAGE = "nextcloud:latest";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = RandomStringUtils.secure().nextAlphanumeric(12);
    private static final int DEFAULT_LOG_LEVEL = 0; // 0 Debug, 1 Info, 2 Warning, 3 Error, 4 Fatal

    public static final String NEXTCLOUD_URL_PROPERTY = "nextcloud.url";
    public static final String NEXTCLOUD_USER_PROPERTY = "nextcloud.user";
    public static final String NEXTCLOUD_PASSWORD_PROPERTY = "nextcloud.password";
    private static final String FEATURE_NAME = "nextcloud-dev-service";
    private static final String FEATURE_DESCRIPTION = "Local Nextcloud instance for development and testing purposes. This is only intended to be used in development mode and should not be used in production! The properties "
            + NEXTCLOUD_URL_PROPERTY + ", " + NEXTCLOUD_USER_PROPERTY + " and "
            + NEXTCLOUD_PASSWORD_PROPERTY
            + " are set to allow connecting to this instance using the standard Nextcloud client libraries.";

    private static final Logger log = Logger.getLogger(NextcloudDevServicesResultBuildItem.class);

    @BuildStep(onlyIfNot = IsProduction.class, onlyIf = DevServicesConfig.Enabled.class)
    public DevServicesResultBuildItem createContainer()
            throws IOException, UnsupportedOperationException, InterruptedException {

        // First check if a nextcloud instance is configured. If it is, no necessity to
        // start the dev service
        final String nextcloudUrl = ConfigProvider.getConfig().getOptionalValue(NEXTCLOUD_URL_PROPERTY, String.class)
                .orElse(null);
        if (nextcloudUrl != null) {
            log.info("Nextcloud url already configured, no need to start Nextcloud dev service");
            return null;
        }

        final String image = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.dev-services.image", String.class)
                .orElse(SERVICE_IMAGE);
        final String user = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.dev-services.user", String.class)
                .orElse(ADMIN_USER);
        final String password = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.dev-services.password", String.class)
                .orElse(ADMIN_PASSWORD);
        final int logLevel = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.dev-services.log-level", Integer.class)
                .orElse(DEFAULT_LOG_LEVEL);
        final List<String> apps = ConfigProvider.getConfig()
                .getOptionalValues("nextcloud.dev-services.apps", String.class)
                .orElse(List.of());
        final Boolean appApiSupport = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.ex-app", Boolean.class).orElse(false);
        final Boolean webhookWorkerEnabled = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.dev-services.enable-webhook-worker", Boolean.class)
                .orElse(false);

        final NextcloudContainer container = new NextcloudContainer(image, user, password);
        container.withApps(apps);
        container.withLogLevel(logLevel);
        if (webhookWorkerEnabled) {
            container.withEnableWebhookWorker();
        }

        if (appApiSupport && !apps.contains("app_api")) {
            container.withApp("app_api");
        }
        container.withReuse(true);
        // Necessary to reach external apps like this one
        container.withExtraHost("host.docker.internal", "host-gateway");
        container.start();

        // Prepare configuration to return
        final String newUrl = "http://%s:%d".formatted(container.getHost(),
                container.getMappedPort(SERVICE_PORT));
        Map<String, String> configOverrides = Map.of( //
                NEXTCLOUD_URL_PROPERTY, newUrl, //
                NEXTCLOUD_USER_PROPERTY, user, //
                NEXTCLOUD_PASSWORD_PROPERTY, password //
        );
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
        final String appPort = ConfigProvider.getConfig().getOptionalValue("quarkus.http.port",
                String.class).orElse("8080");
        final String appPersistentStorage = "tmp/app-storage";
        final String appVersion = ConfigProvider.getConfig().getValue("quarkus.application.version",
                String.class);
        final List<String> appScopes = ConfigProvider.getConfig()
                .getOptionalValues("nextcloud.app-api.scopes", String.class)
                .orElse(List.of("SYSTEM", "FILES", "FILES_SHARING", "USER_INFO",
                        "USER_STATUS", "NOTIFICATIONS", "WEATHER_STATUS", "TALK",
                        "EVENTS_LISTENER"));
        final boolean appIsSystemApp = ConfigProvider.getConfig()
                .getOptionalValue("nextcloud.app-api.system-app", Boolean.class)
                .orElse(false);

        final String nextcloudUrl = configOverrides.get("nextcloud.url");
        container.occ("app_api:daemon:register", daemonName, "Quarkus Dev Services Nextcloud",
                "manual-install", "http", "host.docker.internal", nextcloudUrl);

        // runuser -s /usr/local/bin/php - www-data /var/www/html/occ status

        // Create app-api specific configuration.
        final Map<String, String> appApiConfigOverrides = new HashMap<>(); //
        appApiConfigOverrides.put("quarkus.http.host", "0.0.0.0"); // Necessary to ensure app cloud be
                                                                   // reached from// docker
                                                                   // Variables usually set by exapp daemon
        appApiConfigOverrides.put("aa.version", "1.0.0");
        appApiConfigOverrides.put("app.secret", appSecret);
        appApiConfigOverrides.put("app.id", appName);
        appApiConfigOverrides.put("app.display.name", appName);
        appApiConfigOverrides.put("app.version", appVersion);
        appApiConfigOverrides.put("app.host", "0.0.0.0");
        appApiConfigOverrides.put("app.port", appPort);
        appApiConfigOverrides.put("app.protocol", "http");
        appApiConfigOverrides.put("app.persistent.storage", appPersistentStorage);

        appApiConfigOverrides.putAll(configOverrides);

        // Run this in the background to allow the rest of the application to boot.
        // This is necessary because the app api protocol requires to call healthchech,
        // init and enable endpoints to properly register apps,
        // And these are only available after the rest of the application fully booted.
        Executors.newSingleThreadExecutor().submit(() -> {
            final ObjectMapper om = new ObjectMapper();
            final Map<String, Object> jsonInfoObj = new HashMap<>();
            jsonInfoObj.put("id", appId);
            jsonInfoObj.put("name", appName);
            jsonInfoObj.put("daemon_config_name", daemonName);
            jsonInfoObj.put("version", appVersion);
            jsonInfoObj.put("secret", appSecret);
            jsonInfoObj.put("port", appPort);
            jsonInfoObj.put("system_app", appIsSystemApp ? 1 : 0);
            jsonInfoObj.put("scopes", appScopes);

            try {
                final String jsonInfo = om.writeValueAsString(jsonInfoObj);
                container.occ("app_api:app:register", appName, daemonName, "--json-info", jsonInfo);
                log.infof("Successfully registred external app '%s' in nextcloud", appName);
                container.occ("app_api:app:enable", appName);
                log.infof("Successfully enabled external app '%s' in nextcloud", appName);
            } catch (UnsupportedOperationException | IOException e) {
                log.errorf(e, "Failed to register external app '%s' in nextcloud", appName);
                throw new RuntimeException(e);
            }
        });

        return appApiConfigOverrides;
    }

}
