package io.github.stefanrichterhuber.nextcloudlib.runtime.clients.impl;

import java.net.ProxySelector;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

import com.github.sardine.impl.SardineImpl;

import jakarta.ws.rs.core.MultivaluedMap;

/**
 * This special derivative of SardineImpl sets all necessary headers to work
 * within an NextCloud APPApi environment
 */
public class CustomHeaderSardineImpl extends SardineImpl {
    private final MultivaluedMap<String, String> headers;

    public CustomHeaderSardineImpl(MultivaluedMap<String, String> headers) {
        this.headers = headers;
        super();

    }

    @Override
    protected HttpClientBuilder configure(ProxySelector selector, CredentialsProvider credentials) {
        final List<? extends Header> customHeaders = headers.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> new BasicHeader(e.getKey(), e.getValue().get(0)))
                .toList();

        // Do not pass credentials for basic auth, but generate our own headers from the
        // provided MultivaluedMap
        final HttpClientBuilder builder = super.configure(selector, null).setDefaultHeaders(customHeaders);

        return builder;
    }
}
