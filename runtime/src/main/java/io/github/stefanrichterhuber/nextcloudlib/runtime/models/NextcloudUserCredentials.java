package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import io.quarkus.security.credential.Credential;

public record NextcloudUserCredentials(String loginName, String appPassword, String server) implements Credential {
}