package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceCreateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceUpdateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final InterfaceParamService interfaceParamService;

    public ApiInterfaceController(ApiInterfaceService apiInterfaceService,
                                  InterfaceParamService interfaceParamService) {
        this.apiInterfaceService = apiInterfaceService;
        this.interfaceParamService = interfaceParamService;
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
                result.getData().stream().map(this::toDTO).toList(),
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
        return Result.success(toDTO(apiInterface));
    }

    @GetMapping("/by-data-type/{dataTypeId}")
    public Result<List<ApiInterfaceDTO>> listByDataType(@PathVariable("dataTypeId") Long dataTypeId) {
        return Result.success(apiInterfaceService.listByDataTypeId(dataTypeId).stream()
                .map(this::toDTO)
                .toList());
    }

    @GetMapping("/options")
    public Result<List<ApiInterfaceDTO>> listOptions(
            @RequestParam(name = "vendorId", required = false) Long vendorId,
            @RequestParam(name = "dataTypeId", required = false) Long dataTypeId,
            @RequestParam(name = "status", required = false) String status) {
        return Result.success(apiInterfaceService.listOptions(vendorId, dataTypeId, status).stream()
                .map(this::toDTO)
                .toList());
    }

    @OperationLog(module = "接口管理", operation = "新增接口")
    @PostMapping
    public Result<ApiInterfaceDTO> create(@RequestBody ApiInterfaceCreateReqDTO dto) {
        ApiInterface apiInterface = toEntity(dto);
        if (apiInterface.getInterfaceCode() == null || apiInterface.getInterfaceCode().trim().isEmpty()) {
            return Result.error(400, "接口编码不能为空");
        }
        if (apiInterface.getInterfaceName() == null || apiInterface.getInterfaceName().trim().isEmpty()) {
            return Result.error(400, "接口名称不能为空");
        }

        ApiInterface existing = apiInterfaceService.getByInterfaceCode(apiInterface.getInterfaceCode());
        if (existing != null) {
            return Result.error(400, "接口编码已存在");
        }

        apiInterface.setId(null);
        if (apiInterface.getStatus() == null) {
            apiInterface.setStatus(CommonStatus.ACTIVE);
        }
        if (apiInterface.getSort() == null) {
            apiInterface.setSort(0);
        }
        apiInterfaceService.save(apiInterface);
        return Result.success(toDTO(apiInterface));
    }

    @OperationLog(module = "接口管理", operation = "更新接口")
    @PutMapping("/{id}")
    public Result<ApiInterfaceDTO> update(@PathVariable("id") Long id, @RequestBody ApiInterfaceUpdateReqDTO dto) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return Result.error(404, "接口不存在");
        }
        ApiInterface apiInterface = toEntity(dto);
        apiInterface.setId(id);
        apiInterfaceService.updateById(apiInterface);
        return Result.success(toDTO(apiInterfaceService.getById(id)));
    }

    @OperationLog(module = "接口管理", operation = "删除接口")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return Result.error(404, "接口不存在");
        }
        apiInterfaceService.removeById(id);
        return Result.success(null);
    }

    @OperationLog(module = "接口管理", operation = "更新接口状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        CommonStatus status = CommonStatus.fromCode(body.get("status"));
        if (status == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }

        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return Result.error(404, "接口不存在");
        }

        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(id);
        apiInterface.setStatus(status);
        apiInterfaceService.updateById(apiInterface);
        return Result.success(null);
    }

    @GetMapping("/{id}/schema")
    public Result<Map<String, Object>> getSchema(@PathVariable("id") Long id) {
        Map<String, Object> schema = apiInterfaceService.getInterfaceSchema(id);
        if (schema == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(schema);
    }

    @OperationLog(module = "接口管理", operation = "更新接口Schema")
    @PutMapping("/{id}/schema")
    public Result<Void> updateSchema(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String requestSchema = body.get("requestSchema");
        String responseSchema = body.get("responseSchema");

        if (requestSchema != null && !apiInterfaceService.validateSchema(requestSchema)) {
            return Result.error(400, "请求 Schema 格式无效");
        }
        if (responseSchema != null && !apiInterfaceService.validateSchema(responseSchema)) {
            return Result.error(400, "响应 Schema 格式无效");
        }

        boolean updated = apiInterfaceService.updateSchema(id, requestSchema, responseSchema);
        if (!updated) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(null);
    }

    @OperationLog(module = "接口管理", operation = "验证Schema")
    @PostMapping("/schema/validate")
    public Result<Map<String, Object>> validateSchema(@RequestBody Map<String, String> body) {
        boolean valid = apiInterfaceService.validateSchema(body.get("schema"));
        return Result.success(Map.of("valid", valid));
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

    @GetMapping("/{id}/params")
    public Result<List<InterfaceParamDTO>> listParams(@PathVariable("id") Long id) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(interfaceParamService.listByInterfaceId(id).stream()
                .map(this::toDTO)
                .toList());
    }

    @OperationLog(module = "接口管理", operation = "新增接口参数")
    @PostMapping("/{id}/params")
    public Result<InterfaceParamDTO> addParam(@PathVariable("id") Long id, @RequestBody InterfaceParamDTO dto) {
        InterfaceParam param = toEntity(dto);
        if (param.getParamName() == null || param.getParamName().trim().isEmpty()) {
            return Result.error(400, "参数名不能为空");
        }
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        InterfaceParam existing = interfaceParamService.getByInterfaceIdAndParamName(id, param.getParamName());
        if (existing != null) {
            return Result.error(400, "参数名已存在: " + param.getParamName());
        }
        param.setId(null);
        param.setInterfaceId(id);
        applyParamDefaults(param);
        interfaceParamService.save(param);
        return Result.success(toDTO(param));
    }

    @OperationLog(module = "接口管理", operation = "批量保存接口参数")
    @PutMapping("/{id}/params/batch")
    public Result<Void> batchSaveParams(@PathVariable("id") Long id, @RequestBody List<InterfaceParamDTO> params) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        List<InterfaceParam> entities = params == null ? List.of() : params.stream()
                .map(this::toEntity)
                .toList();
        if (!entities.isEmpty()) {
            List<String> paramNames = entities.stream().map(InterfaceParam::getParamName).toList();
            if (paramNames.size() != paramNames.stream().distinct().count()) {
                return Result.error(400, "参数名不能重复");
            }
        }
        interfaceParamService.batchSave(id, entities);
        return Result.success(null);
    }

    @OperationLog(module = "接口管理", operation = "更新接口参数")
    @PutMapping("/params/{paramId}")
    public Result<InterfaceParamDTO> updateParam(@PathVariable("paramId") Long paramId,
                                                 @RequestBody InterfaceParamDTO dto) {
        InterfaceParam existing = interfaceParamService.getById(paramId);
        if (existing == null) {
            return Result.error(404, "参数定义不存在");
        }
        InterfaceParam param = toEntity(dto);
        param.setId(paramId);
        interfaceParamService.updateById(param);
        return Result.success(toDTO(interfaceParamService.getById(paramId)));
    }

    @OperationLog(module = "接口管理", operation = "删除接口参数")
    @DeleteMapping("/params/{paramId}")
    public Result<Void> deleteParam(@PathVariable("paramId") Long paramId) {
        InterfaceParam existing = interfaceParamService.getById(paramId);
        if (existing == null) {
            return Result.error(404, "参数定义不存在");
        }
        interfaceParamService.removeById(paramId);
        return Result.success(null);
    }

    private void applyParamDefaults(InterfaceParam param) {
        if (param.getParamType() == null) {
            param.setParamType("string");
        }
        if (param.getRequired() == null) {
            param.setRequired(false);
        }
        if (param.getSort() == null) {
            param.setSort(0);
        }
    }

    private ApiInterfaceDTO toDTO(ApiInterface entity) {
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

    private InterfaceParamDTO toDTO(InterfaceParam entity) {
        if (entity == null) {
            return null;
        }
        InterfaceParamDTO dto = new InterfaceParamDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private ApiInterface toEntity(ApiInterfaceCreateReqDTO dto) {
        ApiInterface entity = new ApiInterface();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus()));
        }
        return entity;
    }

    private ApiInterface toEntity(ApiInterfaceUpdateReqDTO dto) {
        ApiInterface entity = new ApiInterface();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus()));
        }
        return entity;
    }

    private InterfaceParam toEntity(InterfaceParamDTO dto) {
        InterfaceParam entity = new InterfaceParam();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }
}
