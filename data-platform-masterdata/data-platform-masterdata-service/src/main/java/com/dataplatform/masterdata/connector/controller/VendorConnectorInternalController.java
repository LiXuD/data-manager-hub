package com.dataplatform.masterdata.connector.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorRuntimeSnapshotDTO;
import com.dataplatform.masterdata.connector.api.feign.VendorConnectorInternalFeignClient;
import com.dataplatform.masterdata.connector.service.VendorConnectorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/masterdata/vendor-configs")
@InternalScope("masterdata:connector-runtime:read")
public class VendorConnectorInternalController implements VendorConnectorInternalFeignClient {
    private final VendorConnectorService service;

    public VendorConnectorInternalController(VendorConnectorService service) {
        this.service = service;
    }

    @Override
    @GetMapping("/{vendorConfigId}/connector-runtime")
    public Result<VendorConnectorRuntimeSnapshotDTO> getRuntimeSnapshot(@PathVariable Long vendorConfigId) {
        return Result.success(service.runtimeSnapshot(vendorConfigId));
    }
}
