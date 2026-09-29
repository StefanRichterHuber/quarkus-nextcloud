package io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.Uni;

public class NextcloudEventInvokerSupportTest {

    private static boolean isDone(CompletionStage<Void> stage) {
        return stage.toCompletableFuture().isDone();
    }

    @Test
    public void testCompleted() throws Exception {
        final CompletionStage<Void> stage = NextcloudEventInvokerSupport.completed();
        assertTrue(isDone(stage));
        assertNull(stage.toCompletableFuture().get());
    }

    @Test
    public void testFromStageNull() throws Exception {
        final CompletionStage<Void> stage = NextcloudEventInvokerSupport.fromStage(null);
        assertTrue(isDone(stage));
        assertNull(stage.toCompletableFuture().get());
    }

    @Test
    public void testFromStageDiscardsValueAndWaitsForCompletion() throws Exception {
        final CompletableFuture<String> source = new CompletableFuture<>();
        final CompletionStage<Void> stage = NextcloudEventInvokerSupport.fromStage(source);
        assertFalse(isDone(stage));

        source.complete("value");
        assertTrue(isDone(stage));
        assertNull(stage.toCompletableFuture().get());
    }

    @Test
    public void testFromStagePropagatesFailure() {
        final IllegalStateException failure = new IllegalStateException("failed");
        final CompletionStage<Void> stage = NextcloudEventInvokerSupport
                .fromStage(CompletableFuture.failedFuture(failure));

        final ExecutionException e = assertThrows(ExecutionException.class,
                () -> stage.toCompletableFuture().get());
        assertSame(failure, e.getCause());
    }

    @Test
    public void testFromUniNull() throws Exception {
        final CompletionStage<Void> stage = NextcloudEventInvokerSupport.fromUni(null);
        assertTrue(isDone(stage));
        assertNull(stage.toCompletableFuture().get());
    }

    @Test
    public void testFromUniSubscribesAndDiscardsValue() throws Exception {
        final AtomicBoolean subscribed = new AtomicBoolean();
        final Uni<String> uni = Uni.createFrom().item(() -> {
            subscribed.set(true);
            return "value";
        });

        final CompletionStage<Void> stage = NextcloudEventInvokerSupport.fromUni(uni);
        assertTrue(subscribed.get(), "Uni was not subscribed");
        assertNull(stage.toCompletableFuture().get());
    }

    @Test
    public void testFromUniPropagatesFailure() {
        final CompletionStage<Void> stage = NextcloudEventInvokerSupport
                .fromUni(Uni.createFrom().failure(new IllegalStateException("failed")));

        final ExecutionException e = assertThrows(ExecutionException.class,
                () -> stage.toCompletableFuture().get());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }
}
