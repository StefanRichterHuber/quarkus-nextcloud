package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.Objects;

import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class NextcloudEventDispatcher {

    @Inject
    Instance<NextcloudEventInvoker> invokers;

    @ActivateRequestContext
    void dispatch(NextcloudEvent<Event> event) {
        final String eventClass = event.event().className();
        for (NextcloudEventInvoker invoker : invokers) {
            for (String invokerEvent : invoker.events()) {
                if (Objects.equals(eventClass, invokerEvent)) {
                    try {
                        invoker.invoke(event);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
