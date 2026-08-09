package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import java.util.List;

public interface VendorConnectorMigrationService {
    List<VendorConnectorMigrationDTO> list(String state);
}
