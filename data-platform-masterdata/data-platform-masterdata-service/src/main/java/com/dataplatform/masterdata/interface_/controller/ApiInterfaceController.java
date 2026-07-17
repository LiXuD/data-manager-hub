package com.dataplatform.masterdata.interface_.controller;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceCreateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceUpdateReqDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import com.dataplatform.masterdata.interface_.service.InterfaceContractService;
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final InterfaceContractService interfaceContractService;

    public ApiInterfaceController(ApiInterfaceService apiInterfaceService,
                                  InterfaceParamService interfaceParamService,
                                  InterfaceContractService interfaceContractService) {
        this.apiInterfaceService = apiInterfaceService;
        this.interfaceParamService = interfaceParamService;
        this.interfaceContractService = interfaceContractService;
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
        if (dto.getRequestSchema() != null || dto.getResponseSchema() != null) {
            return Result.error(400, "请在接口创建后通过契约接口配置请求和响应结构");
        }
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
        if (dto.getRequestSchema() != null || dto.getResponseSchema() != null) {
            return Result.error(400, "Schema快照不可直接修改，请使用接口契约或兼容Schema接口");
        }
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
        if (!UserContext.hasPermission("interface:view")) {
            return Result.error(403, "没有接口契约查看权限");
        }
        Map<String, Object> schema = apiInterfaceService.getInterfaceSchema(id);
        if (schema == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(schema);
    }

    @OperationLog(module = "接口管理", operation = "更新接口Schema")
    @PutMapping("/{id}/schema")
    public Result<Void> updateSchema(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        String requestSchema = body.get("requestSchema");
        String responseSchema = body.get("responseSchema");

        if (requestSchema != null && !apiInterfaceService.validateSchema(requestSchema)) {
            return Result.error(400, "请求 Schema 格式无效");
        }
        if (responseSchema != null && !apiInterfaceService.validateSchema(responseSchema)) {
            return Result.error(400, "响应 Schema 格式无效");
        }

        if (apiInterfaceService.getById(id) == null) {
            return Result.error(404, "接口不存在");
        }
        interfaceContractService.saveLegacySchemas(id, requestSchema, responseSchema);
        return Result.success(null);
    }

    @GetMapping("/{id}/contract")
    public Result<InterfaceContractDTO> getContract(@PathVariable("id") Long id) {
        if (!UserContext.hasPermission("interface:view")) {
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
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        if (apiInterfaceService.getById(id) == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(interfaceContractService.saveContract(id, contract));
    }

    @OperationLog(module = "接口管理", operation = "导入接口Schema")
    @PostMapping("/{id}/contract/import-schema")
    public Result<InterfaceContractDTO> importSchema(@PathVariable("id") Long id) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        if (apiInterfaceService.getById(id) == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(interfaceContractService.importLegacySchemas(id));
    }

    @OperationLog(module = "接口管理", operation = "验证Schema")
    @PostMapping("/schema/validate")
    public Result<Map<String, Object>> validateSchema(@RequestBody Map<String, String> body) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
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
        if (!UserContext.hasPermission("interface:view")) {
            return Result.error(403, "没有接口契约查看权限");
        }
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        return Result.success(flatten(interfaceContractService.getContract(id).getRequestFields()));
    }

    @OperationLog(module = "接口管理", operation = "新增接口参数")
    @PostMapping("/{id}/params")
    public Result<InterfaceParamDTO> addParam(@PathVariable("id") Long id, @RequestBody InterfaceParamDTO dto) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        if (dto == null || dto.getParamName() == null || dto.getParamName().trim().isEmpty()) {
            return Result.error(400, "参数名不能为空");
        }
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        InterfaceContractDTO contract = interfaceContractService.getContract(id);
        if (contract.getRequestFields().stream().anyMatch(field -> dto.getParamName().trim().equals(field.getParamName()))) {
            return Result.error(400, "参数名已存在: " + dto.getParamName());
        }
        dto.setId(null);
        dto.setInterfaceId(id);
        dto.setDirection("REQUEST");
        dto.setParentId(null);
        contract.getRequestFields().add(dto);
        InterfaceContractDTO saved = interfaceContractService.saveContract(id, contract);
        return Result.success(saved.getRequestFields().stream()
                .filter(field -> dto.getParamName().trim().equals(field.getParamName()))
                .findFirst().orElseThrow());
    }

    @OperationLog(module = "接口管理", operation = "批量保存接口参数")
    @PutMapping("/{id}/params/batch")
    public Result<Void> batchSaveParams(@PathVariable("id") Long id, @RequestBody List<InterfaceParamDTO> params) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return Result.error(404, "接口不存在");
        }
        InterfaceContractDTO contract = interfaceContractService.getContract(id);
        contract.setRequestFields(params == null ? List.of() : params);
        interfaceContractService.saveContract(id, contract);
        return Result.success(null);
    }

    @OperationLog(module = "接口管理", operation = "更新接口参数")
    @PutMapping("/params/{paramId}")
    public Result<InterfaceParamDTO> updateParam(@PathVariable("paramId") Long paramId,
                                                 @RequestBody InterfaceParamDTO dto) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        InterfaceParam existing = interfaceParamService.getById(paramId);
        if (existing == null) {
            return Result.error(404, "参数定义不存在");
        }
        InterfaceContractDTO contract = interfaceContractService.getContract(existing.getInterfaceId());
        List<Integer> fieldPath = findFieldPath(contract.getRequestFields(), paramId);
        if (fieldPath == null) {
            return Result.error(404, "请求参数定义不存在");
        }
        InterfaceParamDTO current = fieldAtPath(contract.getRequestFields(), fieldPath);
        mergeField(current, dto);
        InterfaceContractDTO saved = interfaceContractService.saveContract(existing.getInterfaceId(), contract);
        return Result.success(fieldAtPath(saved.getRequestFields(), fieldPath));
    }

    @OperationLog(module = "接口管理", operation = "删除接口参数")
    @DeleteMapping("/params/{paramId}")
    public Result<Void> deleteParam(@PathVariable("paramId") Long paramId) {
        if (!UserContext.hasPermission("interface:edit")) {
            return Result.error(403, "没有接口契约编辑权限");
        }
        InterfaceParam existing = interfaceParamService.getById(paramId);
        if (existing == null) {
            return Result.error(404, "参数定义不存在");
        }
        InterfaceContractDTO contract = interfaceContractService.getContract(existing.getInterfaceId());
        if (!removeField(contract.getRequestFields(), paramId)) {
            return Result.error(404, "请求参数定义不存在");
        }
        interfaceContractService.saveContract(existing.getInterfaceId(), contract);
        return Result.success(null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleContractValidation(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, exception.getMessage()));
    }

    private List<InterfaceParamDTO> flatten(List<InterfaceParamDTO> fields) {
        List<InterfaceParamDTO> result = new ArrayList<>();
        for (InterfaceParamDTO field : fields) {
            result.add(field);
            result.addAll(flatten(field.getChildren()));
        }
        return result;
    }

    private List<Integer> findFieldPath(List<InterfaceParamDTO> fields, Long id) {
        for (int index = 0; index < fields.size(); index++) {
            InterfaceParamDTO field = fields.get(index);
            if (id.equals(field.getId())) {
                return new ArrayList<>(List.of(index));
            }
            List<Integer> nestedPath = findFieldPath(field.getChildren(), id);
            if (nestedPath != null) {
                nestedPath.add(0, index);
                return nestedPath;
            }
        }
        return null;
    }

    private InterfaceParamDTO fieldAtPath(List<InterfaceParamDTO> fields, List<Integer> path) {
        InterfaceParamDTO current = fields.get(path.get(0));
        for (int index = 1; index < path.size(); index++) {
            current = current.getChildren().get(path.get(index));
        }
        return current;
    }

    private boolean removeField(List<InterfaceParamDTO> fields, Long id) {
        for (int index = 0; index < fields.size(); index++) {
            InterfaceParamDTO field = fields.get(index);
            if (id.equals(field.getId())) {
                fields.remove(index);
                return true;
            }
            if (removeField(field.getChildren(), id)) {
                return true;
            }
        }
        return false;
    }

    private void mergeField(InterfaceParamDTO target, InterfaceParamDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("参数定义不能为空");
        }
        target.setParamName(source.getParamName());
        target.setDescription(source.getDescription());
        target.setParamType(source.getParamType());
        target.setArrayItemType(source.getArrayItemType());
        target.setRequired(source.getRequired());
        target.setDefaultValue(source.getDefaultValue());
        target.setValidationRule(source.getValidationRule());
        target.setExampleValue(source.getExampleValue());
        target.setConstraintConfig(source.getConstraintConfig());
        target.setSort(source.getSort());
        if (source.getChildren() != null && !source.getChildren().isEmpty()) {
            target.setChildren(source.getChildren());
        }
    }

    private void applyParamDefaults(InterfaceParam param) {
        if (param.getDirection() == null) {
            param.setDirection("REQUEST");
        }
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
