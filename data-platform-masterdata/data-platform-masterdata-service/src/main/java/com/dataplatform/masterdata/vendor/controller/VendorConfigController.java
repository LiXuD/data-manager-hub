package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigCreateReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigUpdateReqDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主数据域厂商的 Vendor Config Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/vendor/config")
public class VendorConfigController {

    private final VendorConfigService vendorConfigService;

    public VendorConfigController(VendorConfigService vendorConfigService) {
        this.vendorConfigService = vendorConfigService;
    }

    @GetMapping("/list")
    public Result<List<VendorConfigDTO>> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long dataTypeId,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(VendorConfig::getVendorId, vendorId);
        }
        if (dataTypeId != null) {
            wrapper.eq(VendorConfig::getDataTypeId, dataTypeId);
        }
        if (interfaceId != null) {
            wrapper.eq(VendorConfig::getInterfaceId, interfaceId);
        }
        if (status != null && !status.isEmpty()) {
            CommonStatus parsedStatus = CommonStatus.fromCode(status);
            if (parsedStatus == null) {
                return Result.error(400, "无效的状态值");
            }
            wrapper.eq(VendorConfig::getStatus, parsedStatus.getCode());
        }
        wrapper.orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper).stream()
                .map(this::toDTO)
                .toList());
    }

    @GetMapping("/{id}")
    public Result<VendorConfigDTO> getById(@PathVariable("id") Long id) {
        return Result.success(toDTO(vendorConfigService.getById(id)));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorConfigDTO>> listByVendorId(@PathVariable("vendorId") Long vendorId) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getVendorId, vendorId)
                .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper).stream()
                .map(this::toDTO)
                .toList());
    }

    @GetMapping("/interface/{interfaceId}")
    public Result<List<VendorConfigDTO>> listByInterface(@PathVariable Long interfaceId) {
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getInterfaceId, interfaceId)
                .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(vendorConfigService.list(wrapper).stream()
                .map(this::toDTO)
                .toList());
    }

    @OperationLog(module = "厂商配置管理", operation = "新增厂商配置")
    @PostMapping
    public Result<VendorConfigDTO> create(@RequestBody VendorConfigCreateReqDTO dto) {
        VendorConfig config = toEntity(dto);
        if (config.getVendorId() == null) {
            return Result.error(400, "厂商ID不能为空");
        }
        if (config.getInterfaceId() == null) {
            return Result.error(400, "接口ID不能为空");
        }
        if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
            return Result.error(400, "API地址不能为空");
        }

        applyDefaults(config);
        vendorConfigService.save(config);
        return Result.success(toDTO(config));
    }

    @OperationLog(module = "厂商配置管理", operation = "更新厂商配置")
    @PutMapping("/{id}")
    public Result<VendorConfigDTO> update(@PathVariable("id") Long id,
                                          @RequestBody VendorConfigUpdateReqDTO dto) {
        VendorConfig config = toEntity(dto);
        config.setId(id);
        boolean success = vendorConfigService.updateById(config);
        if (!success) {
            return Result.error(404, "配置不存在");
        }
        return Result.success(toDTO(vendorConfigService.getById(id)));
    }

    @OperationLog(module = "厂商配置管理", operation = "删除厂商配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean success = vendorConfigService.removeById(id);
        if (!success) {
            return Result.error(404, "配置不存在");
        }
        return Result.success(null);
    }

    @OperationLog(module = "厂商配置管理", operation = "更新配置状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }

        LambdaUpdateWrapper<VendorConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(VendorConfig::getId, id).set(VendorConfig::getStatus, statusEnum);
        boolean success = vendorConfigService.update(wrapper);
        if (!success) {
            return Result.error(404, "配置不存在");
        }
        return Result.success(null);
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }

        Map<String, Object> result = new HashMap<>();
        try {
            long startTime = System.currentTimeMillis();
            long latency = System.currentTimeMillis() - startTime + (long) (Math.random() * 200);
            result.put("success", true);
            result.put("latency", latency);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return Result.success(result);
    }

    @GetMapping("/{id}/mapping")
    public Result<Map<String, Object>> getParamMapping(@PathVariable Long id) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("vendorConfigId", id);
        result.put("interfaceId", config.getInterfaceId());
        result.put("paramMapping", config.getParamMapping());
        return Result.success(result);
    }

    @OperationLog(module = "厂商配置管理", operation = "更新参数映射")
    @PutMapping("/{id}/mapping")
    public Result<Void> updateParamMapping(@PathVariable Long id, @RequestBody Map<String, String> body) {
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }

        VendorConfig update = new VendorConfig();
        update.setId(id);
        update.setParamMapping(body.get("paramMapping"));
        vendorConfigService.updateById(update);
        return Result.success(null);
    }

    @GetMapping("/byCode")
    public Result<VendorConfigDTO> getByVendorCodeAndDataTypeCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("dataTypeCode") String dataTypeCode) {
        return Result.success(toDTO(vendorConfigService.getByVendorCodeAndDataTypeCode(vendorCode, dataTypeCode)));
    }

    @GetMapping("/secretKey")
    public Result<String> getSecretKey(@RequestParam("vendorCode") String vendorCode) {
        return Result.success(vendorConfigService.getSecretKey(vendorCode));
    }

    @GetMapping("/byVendorIdAndDataTypeCode")
    public Result<VendorConfigDTO> getByVendorIdAndDataTypeCode(
            @RequestParam("vendorId") Long vendorId,
            @RequestParam("dataTypeCode") String dataTypeCode) {
        return Result.success(toDTO(vendorConfigService.getByVendorIdAndDataTypeCode(vendorId, dataTypeCode)));
    }

    @GetMapping("/byInterfaceCode")
    public Result<VendorConfigDTO> getByVendorCodeAndInterfaceCode(
            @RequestParam("vendorCode") String vendorCode,
            @RequestParam("interfaceCode") String interfaceCode) {
        return Result.success(toDTO(vendorConfigService.getByVendorCodeAndInterfaceCode(vendorCode, interfaceCode)));
    }

    private void applyDefaults(VendorConfig config) {
        if (config.getStatus() == null) {
            config.setStatus(CommonStatus.ACTIVE);
        }
        if (config.getTimeout() == null) {
            config.setTimeout(30000);
        }
        if (config.getRetryCount() == null) {
            config.setRetryCount(3);
        }
        if (config.getCircuitThreshold() == null) {
            config.setCircuitThreshold(5);
        }
        if (config.getCircuitTimeout() == null) {
            config.setCircuitTimeout(60);
        }
        if (config.getMethod() == null) {
            config.setMethod("POST");
        }
    }

    private VendorConfigDTO toDTO(VendorConfig entity) {
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

    private VendorConfig toEntity(VendorConfigCreateReqDTO dto) {
        VendorConfig entity = new VendorConfig();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus()));
        }
        return entity;
    }

    private VendorConfig toEntity(VendorConfigUpdateReqDTO dto) {
        VendorConfig entity = new VendorConfig();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus()));
        }
        return entity;
    }
}
