package io.github.stefanrichterhuber.nextcloudlib.runtime.models;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.credential.Credential;

@RegisterForReflection
public record NextcloudUserCredentials(String loginName, String appPassword, String server) implements Credential {
}