package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.smallrye.mutiny.Uni;

/**
 * Static helpers used by the build-time-generated {@link NextcloudEventInvoker}
 * implementations to convert the return value of an
 * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent}-annotated
 * method into a {@code CompletionStage<Void>} signalling the completion of the
 * handler.
 */
public final class NextcloudEventInvokerSupport {

    private NextcloudEventInvokerSupport() {
    }

    /**
     * Used for handler methods returning {@code void}.
     *
     * @return an already completed stage
     */
    public static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Used for handler methods returning a {@link CompletionStage} or
     * {@link CompletableFuture}. The result value of the stage is discarded.
     *
     * @param stage stage returned by the handler, may be {@code null}
     * @return a stage completing when the given stage completes, an already
     *         completed stage if {@code stage} is {@code null}
     */
    public static CompletionStage<Void> fromStage(CompletionStage<?> stage) {
        if (stage == null) {
            return completed();
        }
        return stage.thenApply(v -> null);
    }

    /**
     * Used for handler methods returning a {@link Uni}. The Uni is subscribed and
     * its item is discarded.
     *
     * @param uni Uni returned by the handler, may be {@code null}
     * @return a stage completing when the given Uni emits an item or a failure, an
     *         already completed stage if {@code uni} is {@code null}
     */
    public static CompletionStage<Void> fromUni(Uni<?> uni) {
        if (uni == null) {
            return completed();
        }
        return uni.replaceWithVoid().subscribeAsCompletionStage();
    }
}
