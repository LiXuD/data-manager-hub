package com.dataplatform.common.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;

/** Host hook that limits secret resolution to references declared by the currently executing stage. */
public interface ConnectorStageSecretScope {
    ConnectorStageSecretScope NO_OP = new ConnectorStageSecretScope() {
        @Override public void enter(JsonNode config) { }
        @Override public void leave() { }
    };

    void enter(JsonNode config);
    void leave();
}
