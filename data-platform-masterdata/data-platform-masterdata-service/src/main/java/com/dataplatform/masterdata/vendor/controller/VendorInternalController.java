package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.service.VendorService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 主数据域厂商的 Vendor Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/internal/v1/masterdata/vendors")
@InternalScope("masterdata:read")
public class VendorInternalController implements VendorInternalFeignClient {

    @Autowired
    private VendorService vendorService;

    @Override
    @GetMapping("/{id}")
    public Result<VendorInfoDTO> getById(@PathVariable("id") Long id) {
        VendorInfo entity = vendorService.getById(id);
        if (entity == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(entity));
    }

    @GetMapping("/by-code/{vendorCode}")
    @Override
    public Result<VendorInfoDTO> getByVendorCode(@PathVariable("vendorCode") String vendorCode) {
        VendorInfo entity = vendorService.getByVendorCode(vendorCode);
        if (entity == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(entity));
    }

    private VendorInfoDTO toDTO(VendorInfo entity) {
        VendorInfoDTO dto = new VendorInfoDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }
}
