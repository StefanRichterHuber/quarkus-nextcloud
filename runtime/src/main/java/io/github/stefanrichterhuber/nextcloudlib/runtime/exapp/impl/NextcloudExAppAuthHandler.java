package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse.StatusCode;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.smallrye.config.SmallRyeConfig;
import io.vertx.ext.web.RoutingContext;

public class NextcloudExAppAuthHandler implements io.vertx.core.Handler<RoutingContext> {
    public static final String HEADER_AA_VERSION = "AA-VERSION";
    public static final String HEADER_EX_APP_ID = "EX-APP-ID";
    public static final String HEADER_EX_APP_VERSION = "EX-APP-VERSION";
    public static final String HEADER_AUTHORIZATION_APP_API = "AUTHORIZATION-APP-API";
    public static final String PROPERTY_CREDENTIALS = "nextcloudCredentials";

    private static final Logger LOG = Logger.getLogger(NextcloudExAppAuthHandler.class);

    @Override
    public void handle(RoutingContext event) {

        final String appId = event.request().getHeader(HEADER_EX_APP_ID);
        final String appVersion = event.request().getHeader(HEADER_EX_APP_VERSION);
        final String authorization = event.request().getHeader(HEADER_AUTHORIZATION_APP_API);

        final SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        final NextcloudExappAppConfig appConfig = config.getConfigMapping(NextcloudExappAppConfig.class);
        final String nextcloudUrl = ConfigProvider.getConfig().getValue("nextcloud.url", String.class);

        if (appId == null || appId.isBlank() || !Objects.equals(appId, appConfig.id().get())) {
            LOG.errorf("ExApp request missing / wrong '%s' header", HEADER_EX_APP_ID);
            event.response().setStatusCode(StatusCode.BAD_REQUEST).end();
            return;
        }
        if (appVersion == null || appVersion.isBlank()
                || !Objects.equals(appVersion, appConfig.version().get())) {
            LOG.errorf("ExApp request missing / wrong '%s' header", HEADER_EX_APP_VERSION);
            event.response().setStatusCode(StatusCode.BAD_REQUEST).end();
            return;
        }
        if (authorization == null || authorization.isBlank()) {
            LOG.errorf("ExApp request missing '%s' header", HEADER_AUTHORIZATION_APP_API);
            event.response().setStatusCode(StatusCode.BAD_REQUEST).end();
            return;
        }

        String decodedAuth = new String(Base64.getDecoder().decode(authorization), StandardCharsets.UTF_8);
        if (!decodedAuth.contains(":")) {
            LOG.errorf("ExApp request illegal '%s' header", HEADER_AUTHORIZATION_APP_API);
            event.response().setStatusCode(StatusCode.BAD_REQUEST).end();
            return;
        }
        String[] authParts = decodedAuth.split(":", 2);
        String user = authParts[0];
        String password = authParts[1];
        if (!MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                appConfig.secret().get().getBytes(StandardCharsets.UTF_8))) {

            LOG.errorf("ExApp request wrong password");
            event.response().setStatusCode(StatusCode.UNAUTHORIZED).end();
            return;
        }

        event.put(PROPERTY_CREDENTIALS, new NextcloudUserCredentials(user, password, nextcloudUrl));

        event.next();
    }

}
