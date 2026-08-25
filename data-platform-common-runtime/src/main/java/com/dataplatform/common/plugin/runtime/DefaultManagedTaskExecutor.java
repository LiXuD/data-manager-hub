package com.dataplatform.common.plugin.runtime;

import com.dataplatform.plugin.spi.ManagedTaskExecutor;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class DefaultManagedTaskExecutor implements ManagedTaskExecutor {

    private final Executor executor;

    public DefaultManagedTaskExecutor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public <T> CompletionStage<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, executor);
    }
}
