package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.activation.DataSource;

/**
 * 
 * NextcloudFile: Handler for a file on the Nextcloud instance
 * 
 * @param fileId        Unique ID of the file
 * @param user          User of the file
 * @param path          relative path of the file within the users' file system
 * @param etag          ETAG of the file
 * @param modified      Date of the last modification
 * @param dataSource    Datasource to access the content of the file
 * @param contentLength Expected size of the file
 */
@RegisterForReflection
public record NextcloudFile(Integer fileId, String user, String path, String etag, Date modified, DataSource dataSource,
        Long contentLength) {

    /**
     * Reads the whole content of this Nextcloud file as text with the given Charset
     * 
     * @param cs Charset to use
     * @return Text
     * @throws IOException
     */
    public String readToString(Charset cs) throws IOException {
        try (InputStream is = dataSource.getInputStream()) {
            byte[] content = is.readAllBytes();
            return new String(content, cs);
        }
    }

}
