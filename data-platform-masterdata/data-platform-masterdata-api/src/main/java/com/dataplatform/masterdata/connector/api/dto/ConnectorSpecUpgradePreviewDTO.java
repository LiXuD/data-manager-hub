package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.util.List;

/** Redacted deterministic preview of a SIMPLE connector plugin-version upgrade. */
public record ConnectorSpecUpgradePreviewDTO(
        PluginCoordinate currentPlugin,
        PluginCoordinate targetPlugin,
        boolean valid,
        String errorCode,
        String safeMessage,
        List<SchemaChange> schemaChanges,
        List<ConfigChange> configChanges,
        PlanDiff planDiff,
        String previewSpecHash,
        String compiledSnapshotHash,
        String compileHash) implements Serializable {

    public ConnectorSpecUpgradePreviewDTO {
        schemaChanges = schemaChanges == null ? List.of() : List.copyOf(schemaChanges);
        configChanges = configChanges == null ? List.of() : List.copyOf(configChanges);
    }

    public record PluginCoordinate(String pluginId, String pluginVersion) implements Serializable { }

    public record SchemaChange(
            String path,
            String changeKind,
            String currentType,
            String targetType,
            boolean currentRequired,
            boolean targetRequired,
            boolean secretRef) implements Serializable { }

    public record ConfigChange(
            String path,
            String changeKind,
            String currentSchemaType,
            String targetSchemaType,
            boolean targetRequired,
            boolean secretRef) implements Serializable { }

    public record PlanDiff(
            int addedStageCount,
            int removedStageCount,
            int coordinateChangeCount,
            int configHashChangeCount,
            int artifactDigestChangeCount,
            List<String> changedStageKeys) implements Serializable {

        public PlanDiff {
            changedStageKeys = changedStageKeys == null ? List.of() : List.copyOf(changedStageKeys);
        }
    }
}
