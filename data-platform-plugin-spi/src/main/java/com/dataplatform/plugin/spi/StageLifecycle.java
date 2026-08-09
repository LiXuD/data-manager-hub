package com.dataplatform.plugin.spi;

/** Defines whether a compiled stage instance is shared or created for each request. */
public enum StageLifecycle {
    /** Default for stateless, thread-safe stages. */
    SHARED,
    /** Stateful stage created once per execution and closed after that execution. */
    REQUEST_SCOPED
}
