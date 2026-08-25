package com.dataplatform.plugin.spi;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public interface ManagedTaskExecutor {

    <T> CompletionStage<T> submit(Callable<T> task);
}
