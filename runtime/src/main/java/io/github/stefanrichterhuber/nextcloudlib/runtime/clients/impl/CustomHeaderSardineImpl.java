package io.github.stefanrichterhuber.nextcloudlib.runtime.clients.impl;

import java.io.IOException;
import java.net.ProxySelector;
import java.util.Map;

import org.apache.http.HttpRequestInterceptor;
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
        super();
        this.headers = headers;
    }

    @Override
    protected HttpClientBuilder configure(ProxySelector selector, CredentialsProvider credentials) {
        // Do not pass credentials for basic auth, but generate our own headers from the
        // provided MultivaluedMap. This method is called from the super constructor,
        // before this.headers is assigned, so the headers are resolved lazily per
        // request instead of being passed as default headers here.
        final HttpRequestInterceptor customHeaders = (request, context) -> {
            if (headers == null || "CONNECT".equalsIgnoreCase(request.getRequestLine().getMethod())) {
                return;
            }
            headers.forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    request.addHeader(new BasicHeader(name, values.get(0)));
                }
            });
        };
        return super.configure(selector, null).addInterceptorFirst(customHeaders);
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
