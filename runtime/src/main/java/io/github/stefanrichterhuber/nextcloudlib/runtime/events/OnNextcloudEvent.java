package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;

/**
 * Marks a CDI bean method as a Nextcloud webhook event handler.
 *
 * <p>The annotated method must declare exactly one parameter of type
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent}.
 * The extension automatically registers a webhook listener with Nextcloud
 * on startup and dispatches matching events to the method.
 *
 * @see io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookStartupRegistrar
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnNextcloudEvent {
    /**
     * One or more fully-qualified Nextcloud PHP event class names to listen for,
     * e.g. {@code "OCP\\Files\\Events\\Node\\NodeCreatedEvent"}.
     * Use the constants on
     * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent}
     * for a type-safe reference.
     *
     * @return the event class names to subscribe to
     */
    String[] events();

    /**
     * When {@code true}, a temporary auth token for the triggering user is
     * requested from Nextcloud before the handler is invoked.
     *
     * @return {@code true} to request a temporary auth token
     */
    boolean tokenNeeded() default false;

    /**
     * When {@code true}, a temporary auth token for the triggering user is
     * requested and a {@link NextcloudAuthProvider} instance carrying that token
     * is placed in the request context before the handler is invoked.
     * Setting this to {@code true} implicitly enables {@link #tokenNeeded()}.
     *
     * @return {@code true} to expose a {@link NextcloudAuthProvider} in the request context
     */
    boolean provideAuth() default false;
}
