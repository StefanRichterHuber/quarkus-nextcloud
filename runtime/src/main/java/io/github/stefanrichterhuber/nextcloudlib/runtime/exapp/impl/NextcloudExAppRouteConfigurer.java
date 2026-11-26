package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl;

import java.util.function.Consumer;

import io.vertx.ext.web.Route;

/**
 * Configures the handlers of an ExApp lifecycle route (e.g. the auth handler
 * followed by the endpoint handler). The deployment module provides instances
 * as runtime services; a non-generic type is required, because Quarkus does not
 * accept generic types such as {@link Consumer} as service types.
 */
@FunctionalInterface
public interface NextcloudExAppRouteConfigurer extends Consumer<Route> {
}
