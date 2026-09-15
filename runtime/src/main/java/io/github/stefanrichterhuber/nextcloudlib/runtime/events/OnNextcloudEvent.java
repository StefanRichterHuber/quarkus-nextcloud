package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;

/**
 * Marks a CDI bean method as a Nextcloud webhook event handler.
 *
 * <p>
 * The annotated method must declare exactly one parameter of type
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
     * created by Nextcloud before the handler is invoked. When provided, it is
     * injected into the current {@link NextcloudAuthProvider} using
     * {@link NextcloudAuthProvider#setCredentials(NextcloudUserCredentials)}
     * by the default {@link NextcloudEventDispatcher} implementation
     *
     * @return {@code true} to request a temporary auth token
     */
    boolean requestAuthToken() default false;

    /**
     * An optional filter expression to further restrict which events are dispatched
     * to the handler. e.g.: {@code "{ "user.uid": "admin" }" }. Supports property
     * expressions like
     * {@code  "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\" }" }
     * 
     * @see <a href=
     *      "https://docs.nextcloud.com/server/stable/admin_manual/webhook_listeners/index.html">Nextcloud
     *      Webhook Listeners</a> for the mongo db like filter syntax.
     * 
     * @return Additional filter fo apply
     */
    String filter() default "";
}
