package io.github.stefanrichterhuber.nextcloudlib.runtime.clients.impl;

import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.eclipse.microprofile.config.ConfigProvider;

import com.github.sardine.impl.SardineImpl;

/**
 * This special derivative of SardineImpl sets all necessary headers to work
 * within an NextCloud APPApi environment
 */
public class AppApiAuthenticatedSardineImpl extends SardineImpl {

    public AppApiAuthenticatedSardineImpl(String user, String secret) {
        super(user, secret);

    }

    @Override
    protected HttpClientBuilder configure(ProxySelector selector, CredentialsProvider credentials) {
        // Do not pass credentials for basic auth, but generate our own headers in the
        // next steps
        HttpClientBuilder builder = super.configure(selector, null);

        final String secret = credentials.getCredentials(AuthScope.ANY).getPassword();
        final String user = credentials.getCredentials(AuthScope.ANY).getUserPrincipal().getName();
        final String authHeader = Base64.getEncoder()
                .encodeToString((user + ":" + secret).getBytes(StandardCharsets.UTF_8));

        final String appVersionC = ConfigProvider.getConfig().getValue("app.version", String.class);
        final String appIdC = ConfigProvider.getConfig().getValue("app.id", String.class);

        final Header apiRequest = new BasicHeader("OCS-APIRequest", "true");
        final Header appId = new BasicHeader("EX-APP-ID", appIdC);
        final Header appVersion = new BasicHeader("EX-APP-VERSION", appVersionC);
        final Header auth = new BasicHeader("AUTHORIZATION-APP-API", authHeader);

        builder = builder.setDefaultHeaders(List.of(apiRequest, appId, appVersion, auth));

        return builder;
    }
}
