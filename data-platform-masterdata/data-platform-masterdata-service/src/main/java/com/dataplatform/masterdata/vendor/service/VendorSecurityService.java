package com.dataplatform.masterdata.vendor.service;

import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityCapabilityDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityOrderReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepListDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityVersionDTO;
import java.util.List;
import java.util.Map;

public interface VendorSecurityService {

    VendorSecurityStepListDTO getSteps(Long vendorConfigId);

    VendorSecurityStepListDTO replaceSteps(Long vendorConfigId, Integer expectedVersion,
                                           List<VendorSecurityStepDTO> steps);

    VendorSecurityStepListDTO reorder(Long vendorConfigId, VendorSecurityOrderReqDTO request);

    VendorSecurityPreviewDTO preview(Long vendorConfigId, VendorSecurityPreviewReqDTO request);

    List<VendorSecurityCapabilityDTO> capabilities();

    List<VendorSecurityVersionDTO> history(Long vendorConfigId);

    VendorSecurityStepListDTO rollback(Long vendorConfigId, Long versionId, Integer expectedVersion);

    VendorRuntimeSecurityDTO getRuntimeSecurity(Long vendorConfigId);

    List<SecurityStepConfig> getRuntimeSteps(Long vendorConfigId);

    Map<String, String> resolveSecrets(Long vendorConfigId);
}
