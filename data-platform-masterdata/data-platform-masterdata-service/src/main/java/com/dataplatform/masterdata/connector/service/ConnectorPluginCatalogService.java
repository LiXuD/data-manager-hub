package com.dataplatform.masterdata.connector.service;

import com.dataplatform.access.connector.api.dto.ConnectorPluginActivationSummaryDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPluginDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorPluginVersionDTO;
import com.dataplatform.masterdata.connector.api.dto.PluginArtifactDescriptorDTO;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import java.util.List;

public interface ConnectorPluginCatalogService {
    List<ConnectorPluginDTO> list();
    ConnectorPluginDTO get(String pluginId);
    List<ConnectorPluginVersionDTO> versions(String pluginId);
    ConnectorPluginVersionDTO importVersion(PluginImportRequestDTO request, Long actorId);
    ConnectorPluginVersionDTO verify(String pluginId, String version, Long actorId);
    ConnectorPluginActivationSummaryDTO stage(String pluginId, String version);
    ConnectorPluginActivationSummaryDTO activation(String pluginId, String version);
    ConnectorPluginVersionDTO activate(String pluginId, String version, Long actorId);
    ConnectorPluginVersionDTO disable(String pluginId, String version, Long actorId);
    PluginArtifactDescriptorDTO artifact(String pluginId, String version);
    List<PluginArtifactDescriptorDTO> requiredArtifacts();
}
