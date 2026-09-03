package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.api.Result;
import com.dataplatform.api.PageResult;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigCreateReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigUpdateReqDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.dataplatform.masterdata.vendor.service.VendorConfigConflictException;
import com.dataplatform.masterdata.vendor.service.VendorConfigDTOAssembler;
import com.dataplatform.masterdata.vendor.service.VendorHealthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final VendorHealthService vendorHealthService;
    private final ApiInterfaceService apiInterfaceService;
    private final VendorConfigDTOAssembler dtoAssembler;

    @Autowired
    public VendorConfigController(VendorConfigService vendorConfigService,
                                  VendorHealthService vendorHealthService,
                                  ApiInterfaceService apiInterfaceService,
                                  VendorConfigDTOAssembler dtoAssembler) {
        this.vendorConfigService = vendorConfigService;
        this.vendorHealthService = vendorHealthService;
        this.apiInterfaceService = apiInterfaceService;
        this.dtoAssembler = dtoAssembler;
    }

    @GetMapping("/list")
    public Result<List<VendorConfigDTO>> list(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long dataTypeId,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String status) {
        if (!canView()) {
            return forbiddenPage();
        }
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
        return Result.success(dtoAssembler.toDTOs(vendorConfigService.list(wrapper)));
    }

    @GetMapping("/{id}")
    public Result<VendorConfigDTO> getById(@PathVariable("id") Long id) {
        if (!canView()) return Result.error(403, "没有厂商配置查看权限");
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }
        return Result.success(dtoAssembler.toDTO(config));
    }

    @GetMapping("/vendor/{vendorId}")
    public Result<List<VendorConfigDTO>> listByVendorId(@PathVariable("vendorId") Long vendorId) {
        if (!canView()) return Result.error(403, "没有厂商配置查看权限");
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getVendorId, vendorId)
                .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(dtoAssembler.toDTOs(vendorConfigService.list(wrapper)));
    }

    @GetMapping("/interface/{interfaceId}")
    public Result<List<VendorConfigDTO>> listByInterface(@PathVariable Long interfaceId) {
        if (!canView()) return Result.error(403, "没有厂商配置查看权限");
        LambdaQueryWrapper<VendorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorConfig::getInterfaceId, interfaceId)
                .orderByDesc(VendorConfig::getCreatedAt);
        return Result.success(dtoAssembler.toDTOs(vendorConfigService.list(wrapper)));
    }

    @OperationLog(module = "厂商配置管理", operation = "新增厂商配置")
    @PostMapping
    public Result<VendorConfigDTO> create(@RequestBody VendorConfigCreateReqDTO dto) {
        if (!canAdd()) return Result.error(403, "没有厂商配置新增权限");
        if (dto == null) return Result.error(400, "厂商配置请求不能为空");
        String validationError = validatePolicy(dto);
        if (validationError != null) return Result.error(400, validationError);
        VendorConfig config = toEntity(dto);
        if (config.getVendorId() == null) {
            return Result.error(400, "厂商ID不能为空");
        }
        if (config.getInterfaceId() == null) {
            return Result.error(400, "接口ID不能为空");
        }
        ApiInterface apiInterface = apiInterfaceService.getById(dto.getInterfaceId());
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        if (dto.getDataTypeId() != null && !dto.getDataTypeId().equals(apiInterface.getDataTypeId())) {
            return Result.error(400, "数据类型ID必须与接口数据类型一致");
        }
        if (dto.getDataTypeCode() != null && !dto.getDataTypeCode().isBlank()) {
            Long requestedDataTypeId = vendorConfigService.getDataTypeIdByCode(dto.getDataTypeCode().trim());
            if (requestedDataTypeId == null) {
                return Result.error(400, "数据类型不存在或未启用");
            }
            if (!requestedDataTypeId.equals(apiInterface.getDataTypeId())) {
                return Result.error(400, "数据类型编码必须与接口数据类型一致");
            }
        }
        config.setDataTypeId(apiInterface.getDataTypeId());
        applyDefaults(config);
        config.setRuntimeMode("PLUGIN");
        config.setActiveConnectorVersionId(null);
        config.setConnectorVersion(0);
        config.setStatus(CommonStatus.INACTIVE);
        return Result.success(dtoAssembler.toDTO(vendorConfigService.createBinding(config)));
    }

    @OperationLog(module = "厂商配置管理", operation = "更新厂商配置")
    @PutMapping("/{id}")
    public Result<VendorConfigDTO> update(@PathVariable("id") Long id,
                                          @RequestBody VendorConfigUpdateReqDTO dto) {
        if (!canEdit()) return Result.error(403, "没有厂商配置编辑权限");
        if (dto == null) return Result.error(400, "厂商配置请求不能为空");
        String validationError = validatePolicy(dto);
        if (validationError != null) return Result.error(400, validationError);
        if (vendorConfigService.getById(id) == null) {
            return Result.error(404, "配置不存在");
        }
        VendorConfig config = toEntity(dto);
        config.setId(id);
        config.setStatus(null);
        boolean success = vendorConfigService.updateById(config);
        if (!success) {
            return Result.error(409, "配置更新失败");
        }
        VendorConfig updated = vendorConfigService.getById(id);
        if (updated == null) {
            return Result.error(409, "配置更新后无法读取");
        }
        return Result.success(dtoAssembler.toDTO(updated));
    }

    @OperationLog(module = "厂商配置管理", operation = "删除厂商配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        if (!canDelete()) return Result.error(403, "没有厂商配置删除权限");
        if (vendorConfigService.getById(id) == null) {
            return Result.error(404, "配置不存在");
        }
        if (apiInterfaceService.getByRoutingConfigId(id) != null) {
            return Result.error(409, "主/备用路由正在引用该配置，不能删除");
        }
        boolean success = vendorConfigService.removeById(id);
        if (!success) {
            return Result.error(404, "配置不存在");
        }
        return Result.success(null);
    }

    @OperationLog(module = "厂商配置管理", operation = "更新配置状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!canEdit()) return Result.error(403, "没有厂商配置编辑权限");
        if (body == null) return Result.error(400, "状态请求不能为空");
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }
        if (CommonStatus.ACTIVE.equals(statusEnum) && !vendorConfigService.canActivate(id)) {
            return Result.error(409, "厂商配置必须先发布活动连接器版本才能启用");
        }

        LambdaUpdateWrapper<VendorConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(VendorConfig::getId, id).set(VendorConfig::getStatus, statusEnum.getCode());
        boolean success = vendorConfigService.update(wrapper);
        if (!success) {
            return Result.error(409, "配置状态更新失败");
        }
        return Result.success(null);
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        if (!canEdit()) return Result.error(403, "没有厂商配置编辑权限");
        VendorConfig config = vendorConfigService.getById(id);
        if (config == null) {
            return Result.error(404, "配置不存在");
        }

        return Result.success(vendorHealthService.testConnection(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    @ExceptionHandler({VendorConfigConflictException.class, DuplicateKeyException.class})
    public ResponseEntity<Result<Void>> conflict(RuntimeException exception) {
        String message = exception instanceof VendorConfigConflictException
                ? exception.getMessage() : "当前接口已绑定该厂商";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, message));
    }

    private boolean canView() {
        return UserContext.hasPermission("vendor:view")
                || UserContext.hasPermission("system:admin");
    }

    private boolean canEdit() {
        return UserContext.hasPermission("vendor:edit")
                || UserContext.hasPermission("system:admin");
    }

    private boolean canAdd() {
        return UserContext.hasPermission("vendor:add")
                || UserContext.hasPermission("system:admin");
    }

    private boolean canDelete() {
        return UserContext.hasPermission("vendor:delete")
                || UserContext.hasPermission("system:admin");
    }

    private PageResult<VendorConfigDTO> forbiddenPage() {
        PageResult<VendorConfigDTO> result = PageResult.of(List.of(), 0L, 1, 10);
        result.setCode(403);
        result.setMsg("没有厂商配置查看权限");
        return result;
    }

    private void applyDefaults(VendorConfig config) {
        if (config.getStatus() == null) {
            config.setStatus(CommonStatus.INACTIVE);
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
    }

    private String validatePolicy(VendorConfigUpdateReqDTO dto) {
        return validatePolicy(dto.getTimeout(), dto.getRetryCount(), dto.getCircuitThreshold(), dto.getCircuitTimeout());
    }

    private String validatePolicy(VendorConfigCreateReqDTO dto) {
        return validatePolicy(dto.getTimeout(), dto.getRetryCount(), dto.getCircuitThreshold(), dto.getCircuitTimeout());
    }

    private String validatePolicy(Integer timeout, Integer retryCount,
                                  Integer circuitThreshold, Integer circuitTimeout) {
        if (timeout != null && timeout <= 0) {
            return "超时时间必须大于0";
        }
        if (retryCount != null && retryCount < 0) {
            return "重试次数不能小于0";
        }
        if (circuitThreshold != null && circuitThreshold <= 0) {
            return "熔断阈值必须大于0";
        }
        if (circuitTimeout != null && circuitTimeout <= 0) {
            return "熔断恢复时间必须大于0";
        }
        return null;
    }

    private VendorConfig toEntity(VendorConfigCreateReqDTO dto) {
        VendorConfig entity = new VendorConfig();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private VendorConfig toEntity(VendorConfigUpdateReqDTO dto) {
        VendorConfig entity = new VendorConfig();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }
}
