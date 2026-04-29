package com.dataplatform.vendor.controller;

import com.dataplatform.api.Result;
import com.dataplatform.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.service.VendorService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vendor/internal")
public class VendorInternalController {

    @Autowired
    private VendorService vendorService;

    @GetMapping("/{id}")
    public Result<VendorInfoDTO> getById(@PathVariable("id") Long id) {
        VendorInfo entity = vendorService.getById(id);
        if (entity == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(entity));
    }

    @GetMapping("/code/{vendorCode}")
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
        return dto;
    }
}
