package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;

import jakarta.activation.DataSource;

/**
 * A simple implementation of the DataSource interface that uses a byte array as
 * the source of data.
 */
public class ByteArrayDataSource implements DataSource {

    /**
     * Content type of file
     */
    private final String contentType;
    /**
     * Path of the file
     */
    private final String path;

    private final byte[] content;

    /**
     * Creates a new ByteArrayDataSource with the given path, content type and
     * content.
     * 
     * @param path        the path of the file, used to determine the file name
     * @param contentType the content type of the file, e.g. "text/plain" or
     *                    "application/pdf"
     * @param content     the content of the file as a byte array
     */
    public ByteArrayDataSource(String path, String contentType, byte[] content) {
        this.path = path;
        this.contentType = contentType;
        this.content = content;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException("Unimplemented method 'getOutputStream'");
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getName() {
        return Paths.get(path).getFileName().toString().replace("%20", " ");

    }

}
