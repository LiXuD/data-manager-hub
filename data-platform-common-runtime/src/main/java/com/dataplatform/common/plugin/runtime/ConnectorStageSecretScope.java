package com.dataplatform.common.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;

/** Host hook that limits secret resolution to references declared by the currently executing stage. */
public interface ConnectorStageSecretScope {
    ConnectorStageSecretScope NO_OP = new ConnectorStageSecretScope() {
        @Override public void enter(JsonNode config) { }
        @Override public void leave() { }
    };

    void enter(JsonNode config);

    /**
     * Enters a stage with the exact references derived from its signed Schema.
     * The default preserves compatibility for hosts that do not provide metadata.
     */
    default void enter(JsonNode config, Set<String> secretReferences) { enter(config); }

    void leave();

    /** Values are exposed only to the host sanitizer and never to plugins. */
    default Iterable<String> sensitiveValues() { return List.of(); }
}
