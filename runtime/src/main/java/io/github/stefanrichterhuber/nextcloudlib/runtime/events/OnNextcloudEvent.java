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
 * <h2>Return types</h2>
 * <p>
 * The annotated method may return one of the following types. The result
 * value (if any) is ignored, any other return type fails the build.
 * </p>
 * <ul>
 * <li>{@code void} &ndash; the handler is finished when the method
 * returns.</li>
 * <li>{@link java.util.concurrent.CompletionStage CompletionStage&lt;?&gt;} or
 * {@link java.util.concurrent.CompletableFuture CompletableFuture&lt;?&gt;}
 * &ndash; the handler is finished when the returned stage completes.</li>
 * <li>{@code io.smallrye.mutiny.Uni<?>} &ndash; the returned Uni is
 * subscribed by the extension, the handler is finished when it emits an item
 * or a failure.</li>
 * </ul>
 * <p>
 * Only after the handler is finished, the ephemeral auth token requested with
 * {@link #requestAuthToken()} is deleted (if
 * {@code nextcloud.webhook.cleanup-auth-tokens} is enabled, after the optional
 * {@code nextcloud.webhook.cleanup-auth-token-delay}). Exceptions thrown by the
 * method and failed stages / Unis are logged by the default
 * {@link NextcloudEventDispatcher}.
 * </p>
 * <p>
 * <strong>Note:</strong> The request context &ndash; and with it the
 * request-scoped {@link NextcloudAuthProvider} holding the credentials of the
 * event &ndash; is only active while the annotated method itself runs.
 * Asynchronous continuations run outside of this request context, so
 * request-scoped beans are not available there. Read everything required
 * (e.g. {@link NextcloudAuthProvider#getCredentials()}) before going
 * asynchronous and pass it on explicitly, or use context propagation.
 * </p>
 * <p>
 * To use the Nextcloud services with the event credentials within asynchronous
 * code, run it on a
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.util.CredentialsAwareRequestScopedExecutor}.
 * It starts a new request context for each task and passes the given
 * credentials into its {@link NextcloudAuthProvider}:
 * </p>
 *
 * <pre>{@code
 * public CompletionStage<Void> onFileCreated(NextcloudEvent<?> event) {
 *     // Capture the credentials while the request context is still active
 *     final NextcloudUserCredentials credentials = authProvider.getCredentials();
 *     final Executor executor = new CredentialsAwareRequestScopedExecutor(managedExecutor, credentials);
 *     // All Nextcloud services called within process() act with the event credentials
 *     return CompletableFuture.runAsync(() -> process(event), executor);
 * }
 * }</pre>
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
     * by the default {@link NextcloudEventDispatcher} implementation.
     * See the class documentation on when the token may be deleted again and on
     * the availability of the credentials within asynchronous handlers.
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
