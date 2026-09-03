package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationActionRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationObserveRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationStartRequestDTO;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationRepairCandidateDTO;
import java.util.List;

public interface VendorConnectorMigrationService {
    List<VendorConnectorMigrationDTO> list(String state);

    List<VendorConnectorMigrationRepairCandidateDTO> auditInvalidPrepared();

    int repairInvalidPrepared(Long actorId);

    VendorConnectorMigrationDTO prepare(Long vendorConfigId, Long actorId);

    VendorConnectorMigrationDTO startObservation(Long vendorConfigId,
                                                  VendorConnectorMigrationStartRequestDTO request,
                                                  Long actorId);

    VendorConnectorMigrationDTO observe(Long vendorConfigId,
                                        VendorConnectorMigrationObserveRequestDTO request,
                                        Long actorId);

    VendorConnectorMigrationDTO complete(Long vendorConfigId,
                                         VendorConnectorMigrationActionRequestDTO request,
                                         Long actorId);

    VendorConnectorMigrationDTO rollback(Long vendorConfigId,
                                         VendorConnectorMigrationActionRequestDTO request,
                                         Long actorId);
}
