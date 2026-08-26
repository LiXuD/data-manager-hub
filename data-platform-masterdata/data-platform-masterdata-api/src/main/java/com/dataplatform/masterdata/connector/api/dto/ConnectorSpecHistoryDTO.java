package com.dataplatform.masterdata.connector.api.dto;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redacted immutable connector version history; compiled pipeline details are intentionally absent. */
public record ConnectorSpecHistoryDTO(List<Version> versions) implements Serializable {

    public ConnectorSpecHistoryDTO {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }

    public record Version(
            Long id,
            Long vendorConfigId,
            Integer version,
            String authoringMode,
            ConnectorSpecDTO connectorSpec,
            String specHash,
            String compilerVersion,
            String compileHash,
            String snapshotHash,
            String hashAlgorithm,
            String integrityHash,
            Integer securityVersion,
            String status,
            Long previousVersionId,
            LocalDateTime publishedAt,
            Long publishedBy) implements Serializable {

        public Version {
            connectorSpec = snapshot(connectorSpec);
        }

        @Override
        public ConnectorSpecDTO connectorSpec() {
            return snapshot(connectorSpec);
        }

        private static ConnectorSpecDTO snapshot(ConnectorSpecDTO source) {
            if (source == null) return null;
            ConnectorSpecDTO.PluginRef plugin = source.getPlugin() == null ? null
                    : new ConnectorSpecDTO.PluginRef(source.getPlugin().getPluginId(),
                    source.getPlugin().getPluginVersion());
            List<ConnectorSpecDTO.ResponseMapping> mappings = source.getResponseMapping() == null ? null
                    : source.getResponseMapping().stream().map(mapping ->
                    new ConnectorSpecDTO.ResponseMapping(mapping.getTargetField(), mapping.getSourcePath(),
                            mapping.getSourceType(), deepCopy(mapping.getDefaultValue()),
                            mapping.getTransformType())).toList();
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) deepCopy(source.getConfig());
            return new ConnectorSpecDTO(source.getSpecVersion(), plugin, config, mappings);
        }

        private static Object deepCopy(Object value) {
            if (value == null || value instanceof String || value instanceof Number
                    || value instanceof Boolean) return value;
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, nested) -> {
                    if (!(key instanceof String text)) {
                        throw new IllegalArgumentException("connectorSpec contains a non-string key");
                    }
                    copy.put(text, deepCopy(nested));
                });
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof Iterable<?> iterable) {
                List<Object> copy = new ArrayList<>();
                iterable.forEach(item -> copy.add(deepCopy(item)));
                return Collections.unmodifiableList(copy);
            }
            if (value.getClass().isArray()) {
                List<Object> copy = new ArrayList<>();
                for (int index = 0; index < Array.getLength(value); index++) {
                    copy.add(deepCopy(Array.get(value, index)));
                }
                return Collections.unmodifiableList(copy);
            }
            throw new IllegalArgumentException("connectorSpec contains a non-JSON value");
        }
    }
}
