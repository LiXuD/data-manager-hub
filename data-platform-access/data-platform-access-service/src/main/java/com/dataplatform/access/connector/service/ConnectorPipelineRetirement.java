package com.dataplatform.access.connector.service;

/** Access-local hook for retiring compiled pipelines before releasing their plugin handles. */
public interface ConnectorPipelineRetirement {
    void retirePipelinesUsing(String pluginId, String pluginVersion);
}
