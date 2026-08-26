package com.dataplatform.masterdata.connector.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** User-authored connector product configuration; execution-plan fields are intentionally absent. */
public final class ConnectorSpecDTO implements Serializable {

    private String specVersion;
    private PluginRef plugin;
    private Map<String, Object> config;
    private List<ResponseMapping> responseMapping;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public ConnectorSpecDTO() { }

    public ConnectorSpecDTO(String specVersion, PluginRef plugin, Map<String, Object> config,
                            List<ResponseMapping> responseMapping) {
        this.specVersion = specVersion;
        this.plugin = plugin;
        setConfig(config);
        setResponseMapping(responseMapping);
    }

    public String getSpecVersion() { return specVersion; }
    public void setSpecVersion(String specVersion) { this.specVersion = specVersion; }
    public PluginRef getPlugin() { return plugin; }
    public void setPlugin(PluginRef plugin) { this.plugin = plugin; }
    public Map<String, Object> getConfig() {
        return config == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }
    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? null : new LinkedHashMap<>(config);
    }
    public List<ResponseMapping> getResponseMapping() {
        return responseMapping == null ? null : List.copyOf(responseMapping);
    }
    public void setResponseMapping(List<ResponseMapping> responseMapping) {
        this.responseMapping = responseMapping == null ? null : new ArrayList<>(responseMapping);
    }
    public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields.keySet()); }

    @JsonAnySetter
    public void captureUnknown(String name, Object value) { unknownFields.put(name, value); }

    public static final class PluginRef implements Serializable {
        private String pluginId;
        private String pluginVersion;
        private final Map<String, Object> unknownFields = new LinkedHashMap<>();

        public PluginRef() { }
        public PluginRef(String pluginId, String pluginVersion) {
            this.pluginId = pluginId;
            this.pluginVersion = pluginVersion;
        }
        public String getPluginId() { return pluginId; }
        public void setPluginId(String pluginId) { this.pluginId = pluginId; }
        public String getPluginVersion() { return pluginVersion; }
        public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }
        public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields.keySet()); }
        @JsonAnySetter public void captureUnknown(String name, Object value) { unknownFields.put(name, value); }
    }

    public static final class ResponseMapping implements Serializable {
        private String targetField;
        private String sourcePath;
        private String sourceType;
        private Object defaultValue;
        private String transformType;
        private final Map<String, Object> unknownFields = new LinkedHashMap<>();

        public ResponseMapping() { }
        public ResponseMapping(String targetField, String sourcePath, String sourceType,
                               Object defaultValue, String transformType) {
            this.targetField = targetField;
            this.sourcePath = sourcePath;
            this.sourceType = sourceType;
            this.defaultValue = defaultValue;
            this.transformType = transformType;
        }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        public String getSourcePath() { return sourcePath; }
        public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
        public String getTransformType() { return transformType; }
        public void setTransformType(String transformType) { this.transformType = transformType; }
        public Set<String> unknownFieldNames() { return Set.copyOf(unknownFields.keySet()); }
        @JsonAnySetter public void captureUnknown(String name, Object value) { unknownFields.put(name, value); }
    }
}
