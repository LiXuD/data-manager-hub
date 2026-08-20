package com.dataplatform.masterdata.connector.compiler;

import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDTO;
import com.dataplatform.masterdata.connector.service.VerifiedPluginArtifact;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Deeply snapshotted transaction-view facts consumed by deterministic connector compilation. */
public final class ConnectorSpecCompilationInput {

    private final Long vendorConfigId;
    private final String vendorCode;
    private final String dataTypeCode;
    private final ConnectorSpecDTO connectorSpec;
    private final VerifiedPluginArtifact pluginArtifact;
    private final ConnectorPluginCatalogStatus pluginStatus;
    private final long securityVersion;
    private final boolean securitySnapshotPresent;
    private final List<SecurityStepConfig> securitySteps;
    private final Predicate<String> secretOwnedByVendor;
    private final ConnectorCompilationPurpose purpose;

    public ConnectorSpecCompilationInput(
            Long vendorConfigId,
            String vendorCode,
            String dataTypeCode,
            ConnectorSpecDTO connectorSpec,
            VerifiedPluginArtifact pluginArtifact,
            ConnectorPluginCatalogStatus pluginStatus,
            long securityVersion,
            List<SecurityStepConfig> securitySteps,
            Predicate<String> secretOwnedByVendor,
            ConnectorCompilationPurpose purpose) {
        this.vendorConfigId = vendorConfigId;
        this.vendorCode = vendorCode;
        this.dataTypeCode = dataTypeCode;
        this.connectorSpec = copySpec(Objects.requireNonNull(connectorSpec, "connectorSpec"));
        this.pluginArtifact = Objects.requireNonNull(pluginArtifact, "pluginArtifact");
        this.pluginStatus = Objects.requireNonNull(pluginStatus, "pluginStatus");
        this.securityVersion = securityVersion;
        this.securitySnapshotPresent = securitySteps != null;
        this.securitySteps = securitySteps == null ? List.of()
                : securitySteps.stream().map(ConnectorSpecCompilationInput::copyStep).toList();
        this.secretOwnedByVendor = Objects.requireNonNull(secretOwnedByVendor, "secretOwnedByVendor");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
    }

    public Long vendorConfigId() { return vendorConfigId; }
    public String vendorCode() { return vendorCode; }
    public String dataTypeCode() { return dataTypeCode; }
    public ConnectorSpecDTO connectorSpec() { return copySpec(connectorSpec); }
    public VerifiedPluginArtifact pluginArtifact() { return pluginArtifact; }
    public ConnectorPluginCatalogStatus pluginStatus() { return pluginStatus; }
    public long securityVersion() { return securityVersion; }
    public boolean securitySnapshotPresent() { return securitySnapshotPresent; }
    public List<SecurityStepConfig> securitySteps() {
        return securitySteps.stream().map(ConnectorSpecCompilationInput::copyStep).toList();
    }
    public Predicate<String> secretOwnedByVendor() { return secretOwnedByVendor; }
    public ConnectorCompilationPurpose purpose() { return purpose; }

    private static ConnectorSpecDTO copySpec(ConnectorSpecDTO source) {
        ConnectorSpecDTO.PluginRef plugin = source.getPlugin() == null ? null
                : new ConnectorSpecDTO.PluginRef(source.getPlugin().getPluginId(),
                source.getPlugin().getPluginVersion());
        if (plugin != null) source.getPlugin().unknownFieldNames()
                .forEach(name -> plugin.captureUnknown(name, null));
        List<ConnectorSpecDTO.ResponseMapping> mappings = source.getResponseMapping() == null ? null
                : source.getResponseMapping().stream().map(item -> {
                    if (item == null) return null;
                    ConnectorSpecDTO.ResponseMapping copy = new ConnectorSpecDTO.ResponseMapping(
                            item.getTargetField(), item.getSourcePath(), item.getSourceType(),
                            deepCopy(item.getDefaultValue()), item.getTransformType());
                    item.unknownFieldNames().forEach(name -> copy.captureUnknown(name, null));
                    return copy;
                }).toList();
        ConnectorSpecDTO copy = new ConnectorSpecDTO(source.getSpecVersion(), plugin,
                source.getConfig() == null ? null : deepCopyMap(source.getConfig()), mappings);
        source.unknownFieldNames().forEach(name -> copy.captureUnknown(name, null));
        return copy;
    }

    private static SecurityStepConfig copyStep(SecurityStepConfig source) {
        Objects.requireNonNull(source, "securitySteps cannot contain null");
        SecurityStepConfig copy = new SecurityStepConfig();
        copy.setId(source.getId());
        copy.setDirection(source.getDirection());
        copy.setStepType(source.getStepType());
        copy.setStepName(source.getStepName());
        copy.setSortNo(source.getSortNo());
        copy.setEnabled(source.getEnabled());
        copy.setConfig(deepCopyMap(source.getConfig()));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> copy.put(key, deepCopy(value)));
        return copy;
    }

    private static Object deepCopy(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?>) return value;
        if (value instanceof JsonNode node) return node.deepCopy();
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("Connector maps require string keys");
                }
                copy.put(text, deepCopy(nested));
            });
            return copy;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(deepCopy(item)));
            return copy;
        }
        if (value.getClass().isArray()) {
            List<Object> copy = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                copy.add(deepCopy(Array.get(value, index)));
            }
            return copy;
        }
        throw new IllegalArgumentException("Connector facts must be JSON-compatible");
    }
}
