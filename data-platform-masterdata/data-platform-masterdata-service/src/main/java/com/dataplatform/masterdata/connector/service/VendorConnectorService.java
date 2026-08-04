package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.connector.api.dto.ConnectorValidationResultDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorDraftDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorSaveDraftRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorTestResultDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorVersionDTO;
import java.util.List;

public interface VendorConnectorService {
    VendorConnectorVersionDTO active(Long vendorConfigId);
    VendorConnectorDraftDTO draft(Long vendorConfigId);
    VendorConnectorDraftDTO saveDraft(Long vendorConfigId, VendorConnectorSaveDraftRequestDTO request, Long actorId);
    ConnectorValidationResultDTO validate(Long vendorConfigId);
    VendorConnectorTestResultDTO test(Long vendorConfigId, VendorConnectorTestRequestDTO request, Long actorId);
    VendorConnectorVersionDTO publish(Long vendorConfigId, Integer expectedDraftVersion, Long actorId);
    List<VendorConnectorVersionDTO> history(Long vendorConfigId);
    VendorConnectorVersionDTO rollback(Long vendorConfigId, Integer targetVersion,
                                       Integer expectedConnectorVersion, Long actorId);
    VendorConnectorRuntimeSnapshotDTO runtimeSnapshot(Long vendorConfigId);
}
