package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceCreateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceUpdateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.VendorRoutingUpdateReqDTO;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceDTOAssembler;
import com.dataplatform.masterdata.interface_.service.InterfaceContractService;
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 主数据域接口定义的 Api Interface Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/interface")
public class ApiInterfaceController {

    private final ApiInterfaceService apiInterfaceService;
    private final InterfaceContractService interfaceContractService;
    private final ApiInterfaceDTOAssembler dtoAssembler;

    public ApiInterfaceController(ApiInterfaceService apiInterfaceService,
                                  InterfaceContractService interfaceContractService,
                                  ApiInterfaceDTOAssembler dtoAssembler) {
        this.apiInterfaceService = apiInterfaceService;
        this.interfaceContractService = interfaceContractService;
        this.dtoAssembler = dtoAssembler;
    }

    @GetMapping("/list")
    public PageResult<ApiInterfaceDTO> list(
            @RequestParam(name = "vendorId", required = false) Long vendorId,
            @RequestParam(name = "dataTypeId", required = false) Long dataTypeId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        com.dataplatform.common.result.PageResult<ApiInterfaceVO> result =
                apiInterfaceService.list(vendorId, dataTypeId, status, page, pageSize);
        return PageResult.of(
                dtoAssembler.toDTOs(result.getData()),
                result.getTotal(),
                result.getPage(),
                result.getPageSize());
    }

    @GetMapping("/{id}")
    public Result<ApiInterfaceDTO> getById(@PathVariable("id") Long id) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(dtoAssembler.toDTO(apiInterface));
    }

    @GetMapping("/by-data-type/{dataTypeId}")
    public Result<List<ApiInterfaceDTO>> listByDataType(@PathVariable("dataTypeId") Long dataTypeId) {
        return Result.success(dtoAssembler.toDTOs(apiInterfaceService.listByDataTypeId(dataTypeId)));
    }

    @GetMapping("/options")
    public Result<List<ApiInterfaceDTO>> listOptions(
            @RequestParam(name = "vendorId", required = false) Long vendorId,
            @RequestParam(name = "dataTypeId", required = false) Long dataTypeId,
            @RequestParam(name = "status", required = false) String status) {
        return Result.success(dtoAssembler.toDTOs(apiInterfaceService.listOptions(vendorId, dataTypeId, status)));
    }

    @OperationLog(module = "接口管理", operation = "新增接口")
    @PostMapping
    public Result<ApiInterfaceDTO> create(@RequestBody ApiInterfaceCreateReqDTO dto) {
        if (dto == null) {
            return Result.error(400, "接口请求不能为空");
        }
        if (dto.getRequestSchema() != null || dto.getResponseSchema() != null) {
            return Result.error(400, "请在接口创建后通过契约接口配置请求和响应结构");
        }
        if (hasInvalidStatus(dto.getStatus())) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }
        ApiInterface apiInterface = toEntity(dto);
        if (!StringUtils.hasText(apiInterface.getInterfaceCode())) {
            return Result.error(400, "接口编码不能为空");
        }
        if (!StringUtils.hasText(apiInterface.getInterfaceName())) {
            return Result.error(400, "接口名称不能为空");
        }

        ApiInterface existing = apiInterfaceService.getByInterfaceCode(apiInterface.getInterfaceCode());
        if (existing != null) {
            return Result.error(400, "接口编码已存在");
        }

        apiInterface.setId(null);
        if (CommonStatus.ACTIVE.equals(apiInterface.getStatus())) {
            return Result.error(409, "接口创建后需先绑定并发布主厂商连接器");
        }
        apiInterface.setStatus(CommonStatus.INACTIVE);
        if (apiInterface.getSort() == null) {
            apiInterface.setSort(0);
        }
        if (!apiInterfaceService.save(apiInterface)) {
            return Result.error(409, "接口保存失败");
        }
        return Result.success(dtoAssembler.toDTO(apiInterface));
    }

    @OperationLog(module = "接口管理", operation = "更新接口")
    @PutMapping("/{id}")
    public Result<ApiInterfaceDTO> update(@PathVariable("id") Long id, @RequestBody ApiInterfaceUpdateReqDTO dto) {
        if (dto == null) {
            return Result.error(400, "接口请求不能为空");
        }
        if (dto.getRequestSchema() != null || dto.getResponseSchema() != null) {
            return Result.error(400, "Schema快照不可直接修改，请使用接口契约");
        }
        if (hasInvalidStatus(dto.getStatus())) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }
        if (dto.getInterfaceName() != null && !StringUtils.hasText(dto.getInterfaceName())) {
            return Result.error(400, "接口名称不能为空");
        }
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return Result.error(404, "接口不存在");
        }
        ApiInterface apiInterface = toEntity(dto);
        apiInterface.setId(id);
        if (CommonStatus.ACTIVE.equals(apiInterface.getStatus()) && !apiInterfaceService.canActivate(id)) {
            return Result.error(409, "接口启用前必须配置可用的主厂商连接器");
        }
        if (!apiInterfaceService.updateById(apiInterface)) {
            return Result.error(409, "接口更新失败");
        }
        ApiInterface updated = apiInterfaceService.getById(id);
        if (updated == null) {
            return Result.error(409, "接口更新后无法读取");
        }
        return Result.success(dtoAssembler.toDTO(updated));
    }

    @OperationLog(module = "接口管理", operation = "更新接口厂商路由")
    @PutMapping("/{id}/vendor-routing")
    public Result<ApiInterfaceDTO> updateVendorRouting(@PathVariable("id") Long id,
                                                       @RequestBody VendorRoutingUpdateReqDTO request) {
        return Result.success(dtoAssembler.toDTO(apiInterfaceService.updateVendorRouting(id, request)));
    }

    @OperationLog(module = "接口管理", operation = "删除接口")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return Result.error(404, "接口不存在");
        }
        if (!apiInterfaceService.removeById(id)) {
            return Result.error(409, "接口删除失败");
        }
        return Result.success(null);
    }

    @OperationLog(module = "接口管理", operation = "更新接口状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        if (body == null) {
            return Result.error(400, "状态请求不能为空");
        }
        CommonStatus status = CommonStatus.fromCode(body.get("status"));
        if (status == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }

        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return Result.error(404, "接口不存在");
        }

        if (CommonStatus.ACTIVE.equals(status) && !apiInterfaceService.canActivate(id)) {
            return Result.error(409, "接口启用前必须配置可用的主厂商连接器");
        }

        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(id);
        apiInterface.setStatus(status);
        if (!apiInterfaceService.updateById(apiInterface)) {
            return Result.error(409, "接口状态更新失败");
        }
        return Result.success(null);
    }

    @GetMapping("/{id}/contract")
    public Result<InterfaceContractDTO> getContract(@PathVariable("id") Long id) {
        if (!UserContext.hasPermission("interface:view")
                && !UserContext.hasPermission("system:admin")) {
            return Result.error(403, "没有接口契约查看权限");
        }
        if (apiInterfaceService.getById(id) == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(interfaceContractService.getContract(id));
    }

    @OperationLog(module = "接口管理", operation = "更新接口调用契约")
    @PutMapping("/{id}/contract")
    public Result<InterfaceContractDTO> updateContract(@PathVariable("id") Long id,
                                                       @RequestBody InterfaceContractDTO contract) {
        if (!UserContext.hasPermission("interface:edit")
                && !UserContext.hasPermission("system:admin")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        if (contract == null) {
            return Result.error(400, "接口契约请求不能为空");
        }
        if (apiInterfaceService.getById(id) == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(interfaceContractService.saveContract(id, contract));
    }

    @GetMapping("/{id}/stats")
    public Result<Map<String, Object>> getCallStats(
            @PathVariable("id") Long id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Map<String, Object> stats = apiInterfaceService.getCallStats(id, startTime, endTime);
        if (stats == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(stats);
    }

    @GetMapping("/{id}/stats/daily")
    public Result<List<Map<String, Object>>> getDailyCallStats(
            @PathVariable("id") Long id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(apiInterfaceService.getDailyCallStats(id, startTime, endTime));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleContractValidation(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    private ApiInterface toEntity(ApiInterfaceCreateReqDTO dto) {
        ApiInterface entity = new ApiInterface();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getInterfaceCode() != null) {
            entity.setInterfaceCode(dto.getInterfaceCode().trim());
        }
        if (dto.getInterfaceName() != null) {
            entity.setInterfaceName(dto.getInterfaceName().trim());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus().trim()));
        }
        return entity;
    }

    private ApiInterface toEntity(ApiInterfaceUpdateReqDTO dto) {
        ApiInterface entity = new ApiInterface();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getInterfaceName() != null) {
            entity.setInterfaceName(dto.getInterfaceName().trim());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus().trim()));
        }
        return entity;
    }

    private boolean hasInvalidStatus(String status) {
        return status != null && CommonStatus.fromCode(status.trim()) == null;
    }

}
