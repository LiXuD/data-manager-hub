package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretResolutionDTO;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretResolutionRequestDTO;
import com.dataplatform.masterdata.connector.service.ConnectorSecretReferenceService;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.dataplatform.masterdata.vendor.service.VendorSecurityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/masterdata/vendor-security")
@InternalScope("masterdata:vendor-security:read")
public class VendorSecurityInternalController implements VendorSecurityInternalFeignClient {

    private final VendorSecurityService securityService;
    private final ConnectorSecretReferenceService connectorSecretService;

    public VendorSecurityInternalController(VendorSecurityService securityService,
                                            ConnectorSecretReferenceService connectorSecretService) {
        this.securityService = securityService;
        this.connectorSecretService = connectorSecretService;
    }

    @Override
    @GetMapping("/{configId}")
    public Result<VendorRuntimeSecurityDTO> getRuntimeSecurity(@PathVariable("configId") Long configId) {
        return Result.success(securityService.getRuntimeSecurity(configId));
    }

    @Override
    @PostMapping("/connector-secrets/resolve")
    @InternalScope("masterdata:vendor-secret:read")
    public Result<ConnectorSecretResolutionDTO> resolveConnectorSecrets(
            @RequestBody ConnectorSecretResolutionRequestDTO request) {
        if (request == null || request.getVendorConfigId() == null) {
            throw new IllegalArgumentException("vendorConfigId不能为空");
        }
        ConnectorSecretResolutionDTO response = new ConnectorSecretResolutionDTO();
        response.setResolvedSecrets(connectorSecretService.resolve(
                request.getVendorConfigId(), request.getSecretRefs()));
        return Result.success(response);
    }
}
