package com.dataplatform.masterdata.controller;

import com.dataplatform.api.Result;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.api.feign.MasterdataFeignClient;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.dataplatform.masterdata.vendor.service.VendorService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MasterdataContractController implements MasterdataFeignClient {

    private final VendorService vendorService;
    private final VendorConfigService vendorConfigService;
    private final ApiInterfaceService apiInterfaceService;

    public MasterdataContractController(VendorService vendorService,
                                        VendorConfigService vendorConfigService,
                                        ApiInterfaceService apiInterfaceService) {
        this.vendorService = vendorService;
        this.vendorConfigService = vendorConfigService;
        this.apiInterfaceService = apiInterfaceService;
    }

    @Override
    public Result<VendorInfoDTO> getVendorByCode(String vendorCode) {
        return Result.success(toVendorInfoDTO(vendorService.getByVendorCode(vendorCode)));
    }

    @Override
    public Result<VendorConfigDTO> getVendorConfigByInterfaceCode(String vendorCode, String interfaceCode) {
        VendorConfig config = vendorConfigService.getByVendorCodeAndInterfaceCode(vendorCode, interfaceCode);
        return Result.success(toVendorConfigDTO(config));
    }

    @Override
    public Result<ApiInterfaceDTO> getInterfaceByCode(String interfaceCode) {
        return Result.success(toApiInterfaceDTO(apiInterfaceService.getByInterfaceCode(interfaceCode)));
    }

    @Override
    public Result<ApiInterfaceDTO> getInterfaceById(Long id) {
        return Result.success(toApiInterfaceDTO(apiInterfaceService.getById(id)));
    }

    private VendorInfoDTO toVendorInfoDTO(VendorInfo entity) {
        if (entity == null) {
            return null;
        }
        VendorInfoDTO dto = new VendorInfoDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private VendorConfigDTO toVendorConfigDTO(VendorConfig entity) {
        if (entity == null) {
            return null;
        }
        VendorConfigDTO dto = new VendorConfigDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private ApiInterfaceDTO toApiInterfaceDTO(ApiInterface entity) {
        if (entity == null) {
            return null;
        }
        ApiInterfaceDTO dto = new ApiInterfaceDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }
}
