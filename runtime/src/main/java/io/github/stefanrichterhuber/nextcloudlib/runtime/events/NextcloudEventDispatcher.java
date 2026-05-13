package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.quarkus.arc.Arc;

/**
 * Dispatches incoming Nextcloud webhook events to all matching
 * {@link OnNextcloudEvent}-annotated CDI bean methods.
 *
 * <p>
 * Instances are created by {@link NextcloudEventRecorder} at {@code STATIC_INIT}
 * and exposed as a synthetic {@code @Singleton} CDI bean. The handler descriptors
 * are determined entirely at build time; no classpath scanning occurs at runtime.
 * </p>
 *
 * <p>
 * Dispatch uses build-time-generated {@link NextcloudEventInvoker} classes — there
 * is no {@code Method.invoke()} at request time.
 * </p>
 */
public class NextcloudEventDispatcher {

    private static final Logger LOG = Logger.getLogger(NextcloudEventDispatcher.class);

    /**
     * Pairs a CDI bean class with its pre-instantiated invoker and the set of
     * event class names it listens for.
     */
    private record RuntimeHandler(Class<?> beanClass, NextcloudEventInvoker invoker,
            Set<String> eventClassNames) {
    }

    private final List<RuntimeHandler> handlers;

    public NextcloudEventDispatcher(List<HandlerDescriptor> descriptors) {
        this.handlers = descriptors.stream()
                .map(NextcloudEventDispatcher::loadHandler)
                .collect(Collectors.toList());
    }

    private static RuntimeHandler loadHandler(HandlerDescriptor desc) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> beanClass = cl.loadClass(desc.declaringClassName());
            @SuppressWarnings("unchecked")
            Class<? extends NextcloudEventInvoker> invokerClass =
                    (Class<? extends NextcloudEventInvoker>) cl.loadClass(desc.invokerClassName());
            NextcloudEventInvoker invoker = invokerClass.getDeclaredConstructor().newInstance();
            return new RuntimeHandler(beanClass, invoker, Set.copyOf(desc.eventClassNames()));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load invoker " + desc.invokerClassName(), e);
        }
    }

    /**
     * Returns the set of Nextcloud event class names for which at least one
     * handler method was registered. Used by {@link NextcloudWebhookRegistrar}
     * to determine which webhooks to register with Nextcloud.
     */
    public Set<String> getEventClassNames() {
        return handlers.stream()
                .flatMap(h -> h.eventClassNames().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Dispatches {@code event} to every handler whose configured event class names
     * include {@code event.event().className()}.
     *
     * @param event the deserialized Nextcloud event received from the webhook call
     */
    public void dispatch(NextcloudEvent<?> event) {
        String eventClass = event.event().className();
        for (RuntimeHandler handler : handlers) {
            if (!handler.eventClassNames().contains(eventClass)) {
                continue;
            }
            try {
                @SuppressWarnings("unchecked")
                Object bean = Arc.container().select((Class<Object>) handler.beanClass()).get();
                handler.invoker().invoke(bean, event);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to dispatch event %s to %s",
                        eventClass, handler.beanClass().getName());
            }
        }
    }
}
