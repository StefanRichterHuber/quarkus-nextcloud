package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

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

        final MultivaluedMap<String, String> generated = provider.getCredentials().getRequiredHeaders();
        final MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        result.putAll(generated);
        result.putAll(clientOutgoingHeaders);

        return result;
    }
}
