package io.github.stefanrichterhuber.nextcloudlib.runtime.clients.impl;

import java.io.IOException;
import java.net.ProxySelector;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

import com.github.sardine.impl.SardineImpl;
import com.github.sardine.impl.handler.VoidResponseHandler;

import jakarta.ws.rs.core.MultivaluedMap;

/**
 * This special derivative of SardineImpl sets all necessary headers to work
 * within an NextCloud APPApi environment
 */
public class CustomHeaderSardineImpl extends SardineImpl {
    private final MultivaluedMap<String, String> headers;

    /**
     * Creates a new Sardine instance
     * 
     * @param headers HTTP Headers to apply to each request
     */
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

    /**
     * Workaround: Current version of sardine just silently drops headers for delete
     * requests
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void delete(String url, Map<String, String> headers) throws IOException {
        HttpDelete del = new HttpDelete(url);
        ResponseHandler handler = new VoidResponseHandler();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            del.addHeader(new BasicHeader(header.getKey(), header.getValue()));
        }
        try {
            this.execute(del, handler);
        } catch (HttpResponseException e) {
            throw e;
        }
    }
}
