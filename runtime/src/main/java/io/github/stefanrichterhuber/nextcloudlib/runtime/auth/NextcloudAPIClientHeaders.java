package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

/**
 * Decorates out-going rest service calls to nextcloud with the necessary
 * authentication headers, depending on the configured authentication mode.
 */
@ApplicationScoped
public class NextcloudAPIClientHeaders implements ClientHeadersFactory {

    @Inject
    NextcloudAuthProvider provider;

    @Inject
    NextcloudExappConfig exappConfig;

    @Inject
    NextcloudExappAppConfig appConfig;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {

        final MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        if (exappConfig.enabled()) {
            result.putSingle("EX-APP-ID", appConfig.id().get());
            result.putSingle("EX-APP-VERSION", appConfig.version().get());
            result.putSingle("OCS-APIRequest", "true");
            result.putSingle("User-Agent", appConfig.id().get());

            final String user = provider.getUser();
            final String secret = appConfig.secret().get();
            final String auth = user + ":" + secret;
            final String authHeader = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            result.putSingle("AUTHORIZATION-APP-API", authHeader);
        } else {
            final String authHeader = provider.getAuthorizationHeader();
            result.putSingle("Authorization", authHeader);
            result.putSingle("OCS-APIRequest", "true");
        }
        return result;
    }
}
