package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 主数据域厂商的 Vendor Config Internal Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/internal/v1/masterdata/vendor-configs")
@InternalScope("masterdata:read")
public class VendorConfigInternalController implements VendorConfigInternalFeignClient {

    @Autowired
    private VendorConfigService vendorConfigService;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Override
    @GetMapping
    public Result<List<VendorConfigDTO>> list(Long vendorId, Long dataTypeId, Long interfaceId, String status) {
        CommonStatus parsedStatus = status != null && !status.isBlank() ? CommonStatus.fromCode(status) : null;
        if (status != null && !status.isBlank() && parsedStatus == null) {
            return Result.error(400, "无效的状态值");
        }
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(vendorId != null, VendorConfig::getVendorId, vendorId)
                .eq(dataTypeId != null, VendorConfig::getDataTypeId, dataTypeId)
                .eq(interfaceId != null, VendorConfig::getInterfaceId, interfaceId)
                .eq(parsedStatus != null, VendorConfig::getStatus,
                        parsedStatus != null ? parsedStatus.getCode() : null)
                .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper).stream().map(this::toDTO).toList());
    }

    @Override
    @GetMapping("/by-vendor-and-data-type")
    public Result<VendorConfigDTO> getByVendorIdAndDataTypeCode(
            @RequestParam("vendorId") Long vendorId,
            @RequestParam("dataTypeCode") String dataTypeCode) {
        VendorConfig config = vendorConfigService.getByVendorIdAndDataTypeCode(vendorId, dataTypeCode);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    @Override
    @GetMapping("/by-interface-code")
    public Result<VendorConfigDTO> getByVendorCodeAndInterfaceCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode) {
        VendorConfig config = vendorConfigService.getByVendorCodeAndInterfaceCode(vendorCode, interfaceCode);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    @Override
    @GetMapping("/by-code")
    public Result<VendorConfigDTO> getByVendorCodeAndDataTypeCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataTypeCode") String dataTypeCode) {
        VendorConfig config = vendorConfigService.getByVendorCodeAndDataTypeCode(vendorCode, dataTypeCode);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    @Override
    @GetMapping("/secret-key")
    @InternalScope("masterdata:vendor-secret:read")
    public Result<String> getSecretKey(@RequestParam("vendorCode") String vendorCode) {
        String secretKey = vendorConfigService.getSecretKey(vendorCode);
        return Result.success(secretKey);
    }

    @Override
    @GetMapping("/{id}")
    public Result<VendorConfigDTO> getById(@PathVariable("id") Long id) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(config));
    }

    private VendorConfigDTO toDTO(VendorConfig entity) {
        VendorConfigDTO dto = new VendorConfigDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        if (entity.getDataTypeId() != null) {
            DataType dataType = dataTypeMapper.selectById(entity.getDataTypeId());
            if (dataType != null) {
                dto.setDataTypeCode(dataType.getDataTypeCode());
            }
        }
        return dto;
    }
}
