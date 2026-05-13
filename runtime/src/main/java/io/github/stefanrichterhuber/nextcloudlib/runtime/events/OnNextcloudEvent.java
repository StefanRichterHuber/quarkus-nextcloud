package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CDI bean method as a Nextcloud webhook event handler.
 *
 * <p>
 * The annotated method must declare exactly one parameter of type
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent}.
 * The extension will automatically register a webhook listener with Nextcloud
 * on startup and dispatch matching events to the method.
 * </p>
 *
 * <pre>{@code
 * @ApplicationScoped
 * public class MyBean {
 *
 *     @OnNextcloudEvent({NextcloudEvent.FileNodeCreatedEvent, NextcloudEvent.FileNodeDeletedEvent})
 *     public void onFileChanged(NextcloudEvent<?> event) {
 *         // handle event
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnNextcloudEvent {
    /**
     * One or more fully-qualified Nextcloud PHP event class names to listen for,
     * e.g. {@code "OCP\\Files\\Events\\Node\\NodeCreatedEvent"}.
     * Use the constants on {@link io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent}
     * for a type-safe reference.
     */
    String[] value();
}
