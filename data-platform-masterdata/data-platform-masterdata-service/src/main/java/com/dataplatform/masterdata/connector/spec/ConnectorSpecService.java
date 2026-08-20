package com.dataplatform.masterdata.connector.spec;

import com.dataplatform.masterdata.connector.api.dto.ConnectorExecutionPlanDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecCatalogDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConversionPreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecConvertRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecDraftViewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecHistoryDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecPublishRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecRollbackRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecSaveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecUpgradePreviewRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecValidationDTO;
import com.dataplatform.masterdata.connector.api.dto.ConnectorSpecVersionDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestResultDTO;
import java.util.List;

public interface ConnectorSpecService {
    ConnectorSpecCatalogDTO catalog(Long configId);
    List<ConnectorSpecCatalogDTO.Version> versions(Long configId, String pluginId);
    ConnectorSpecDraftViewDTO draft(Long configId);
    ConnectorSpecDraftViewDTO saveDraft(Long configId, ConnectorSpecSaveRequestDTO request, Long actorId);
    ConnectorSpecValidationDTO validate(Long configId);
    ConnectorExecutionPlanDTO executionPlan(Long configId, Integer version);
    VendorConnectorTestResultDTO test(Long configId, ConnectorSpecTestRequestDTO request, Long actorId);
    ConnectorSpecVersionDTO publish(Long configId, ConnectorSpecPublishRequestDTO request, Long actorId);
    ConnectorSpecHistoryDTO history(Long configId);
    ConnectorSpecVersionDTO rollback(Long configId, Integer version,
                                     ConnectorSpecRollbackRequestDTO request, Long actorId);
    ConnectorSpecUpgradePreviewDTO upgradePreview(
            Long configId, ConnectorSpecUpgradePreviewRequestDTO request);
    ConnectorSpecConversionPreviewDTO convertPreview(Long configId);
    ConnectorSpecDraftViewDTO convert(
            Long configId, ConnectorSpecConvertRequestDTO request, Long actorId);
}
