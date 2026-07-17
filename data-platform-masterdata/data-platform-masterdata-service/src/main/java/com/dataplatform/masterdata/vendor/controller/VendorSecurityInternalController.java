package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorSecurityInternalFeignClient;
import com.dataplatform.masterdata.vendor.service.VendorSecurityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/masterdata/vendor-security")
@InternalScope("masterdata:vendor-security:read")
public class VendorSecurityInternalController implements VendorSecurityInternalFeignClient {

    private final VendorSecurityService securityService;

    public VendorSecurityInternalController(VendorSecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    @GetMapping("/{configId}")
    public Result<VendorRuntimeSecurityDTO> getRuntimeSecurity(@PathVariable("configId") Long configId) {
        return Result.success(securityService.getRuntimeSecurity(configId));
    }
}
