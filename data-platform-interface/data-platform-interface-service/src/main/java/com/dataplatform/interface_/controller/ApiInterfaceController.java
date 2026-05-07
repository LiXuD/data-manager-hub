package com.dataplatform.interface_.controller;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.entity.ApiInterfaceVO;
import com.dataplatform.interface_.entity.InterfaceParam;
import com.dataplatform.interface_.service.ApiInterfaceService;
import com.dataplatform.interface_.service.InterfaceParamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/interface")
public class ApiInterfaceController {

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    @Autowired
    private InterfaceParamService interfaceParamService;

    @GetMapping("/list")
    public PageResult<ApiInterfaceVO> list(
            @RequestParam(name = "vendorId", required = false) Long vendorId,
            @RequestParam(name = "dataTypeId", required = false) Long dataTypeId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        return apiInterfaceService.list(vendorId, dataTypeId, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<ApiInterface>> getById(@PathVariable Long id) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        return ResponseEntity.ok(Result.success(apiInterface));
    }

    @GetMapping("/by-data-type/{dataTypeId}")
    public Result<List<ApiInterface>> listByDataType(@PathVariable Long dataTypeId) {
        return Result.success(apiInterfaceService.listByDataTypeId(dataTypeId));
    }

    @OperationLog(module = "接口管理", operation = "新增接口")
    @PostMapping
    public ResponseEntity<Result<ApiInterface>> create(@RequestBody ApiInterface apiInterface) {
        if (apiInterface.getInterfaceCode() == null || apiInterface.getInterfaceCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口编码不能为空"));
        }
        if (apiInterface.getInterfaceName() == null || apiInterface.getInterfaceName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口名称不能为空"));
        }

        ApiInterface existing = apiInterfaceService.getByInterfaceCode(apiInterface.getInterfaceCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "接口编码已存在"));
        }

        apiInterface.setId(null);
        if (apiInterface.getStatus() == null) {
            apiInterface.setStatus(CommonStatus.ACTIVE);
        }
        if (apiInterface.getSort() == null) {
            apiInterface.setSort(0);
        }
        apiInterfaceService.save(apiInterface);
        return ResponseEntity.ok(Result.success(apiInterface));
    }

    @OperationLog(module = "接口管理", operation = "更新接口")
    @PutMapping("/{id}")
    public ResponseEntity<Result<ApiInterface>> update(@PathVariable Long id, @RequestBody ApiInterface apiInterface) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        apiInterface.setId(id);
        apiInterfaceService.updateById(apiInterface);
        return ResponseEntity.ok(Result.success(apiInterfaceService.getById(id)));
    }

    @OperationLog(module = "接口管理", operation = "删除接口")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        apiInterfaceService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "接口管理", operation = "更新接口状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: active, inactive"));
        }

        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }

        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(id);
        apiInterface.setStatus(statusEnum);
        apiInterfaceService.updateById(apiInterface);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/{id}/schema")
    public ResponseEntity<Result<Map<String, Object>>> getSchema(@PathVariable Long id) {
        Map<String, Object> schema = apiInterfaceService.getInterfaceSchema(id);
        if (schema == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        return ResponseEntity.ok(Result.success(schema));
    }

    @OperationLog(module = "接口管理", operation = "更新接口Schema")
    @PutMapping("/{id}/schema")
    public ResponseEntity<Result<Void>> updateSchema(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String requestSchema = body.get("requestSchema");
        String responseSchema = body.get("responseSchema");

        // 验证 Schema 格式
        if (requestSchema != null && !apiInterfaceService.validateSchema(requestSchema)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "请求 Schema 格式无效"));
        }
        if (responseSchema != null && !apiInterfaceService.validateSchema(responseSchema)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "响应 Schema 格式无效"));
        }

        boolean updated = apiInterfaceService.updateSchema(id, requestSchema, responseSchema);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "接口管理", operation = "验证Schema")
    @PostMapping("/schema/validate")
    public Result<Map<String, Object>> validateSchema(@RequestBody Map<String, String> body) {
        String schema = body.get("schema");
        boolean valid = apiInterfaceService.validateSchema(schema);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("valid", valid);
        return Result.success(result);
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Result<Map<String, Object>>> getCallStats(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Map<String, Object> stats = apiInterfaceService.getCallStats(id, startTime, endTime);
        if (stats == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        return ResponseEntity.ok(Result.success(stats));
    }

    @GetMapping("/{id}/stats/daily")
    public ResponseEntity<Result<List<Map<String, Object>>>> getDailyCallStats(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        List<Map<String, Object>> stats = apiInterfaceService.getDailyCallStats(id, startTime, endTime);
        return ResponseEntity.ok(Result.success(stats));
    }

    /**
     * 获取接口的所有参数定义
     */
    @GetMapping("/{id}/params")
    public ResponseEntity<Result<List<InterfaceParam>>> listParams(@PathVariable Long id) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        List<InterfaceParam> params = interfaceParamService.listByInterfaceId(id);
        return ResponseEntity.ok(Result.success(params));
    }

    /**
     * 新增一个参数定义
     */
    @OperationLog(module = "接口管理", operation = "新增接口参数")
    @PostMapping("/{id}/params")
    public ResponseEntity<Result<InterfaceParam>> addParam(@PathVariable Long id,
                                                            @RequestBody InterfaceParam param) {
        if (param.getParamName() == null || param.getParamName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "参数名不能为空"));
        }
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        InterfaceParam existing = interfaceParamService.getByInterfaceIdAndParamName(id, param.getParamName());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "参数名已存在: " + param.getParamName()));
        }
        param.setId(null);
        param.setInterfaceId(id);
        if (param.getParamType() == null) {
            param.setParamType("string");
        }
        if (param.getRequired() == null) {
            param.setRequired(false);
        }
        if (param.getSort() == null) {
            param.setSort(0);
        }
        interfaceParamService.save(param);
        return ResponseEntity.ok(Result.success(param));
    }

    /**
     * 批量保存参数定义（覆盖更新）
     */
    @OperationLog(module = "接口管理", operation = "批量保存接口参数")
    @PutMapping("/{id}/params/batch")
    public ResponseEntity<Result<Void>> batchSaveParams(@PathVariable Long id,
                                                          @RequestBody List<InterfaceParam> params) {
        ApiInterface apiInterface = apiInterfaceService.getById(id);
        if (apiInterface == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }
        if (params != null && !params.isEmpty()) {
            List<String> paramNames = params.stream()
                    .map(InterfaceParam::getParamName)
                    .collect(Collectors.toList());
            if (paramNames.size() != paramNames.stream().distinct().count()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Result.error(400, "参数名不能重复"));
            }
        }
        interfaceParamService.batchSave(id, params);
        return ResponseEntity.ok(Result.success(null));
    }

    /**
     * 更新单个参数定义
     */
    @OperationLog(module = "接口管理", operation = "更新接口参数")
    @PutMapping("/params/{paramId}")
    public ResponseEntity<Result<InterfaceParam>> updateParam(@PathVariable Long paramId,
                                                                @RequestBody InterfaceParam param) {
        InterfaceParam existing = interfaceParamService.getById(paramId);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "参数定义不存在"));
        }
        param.setId(paramId);
        interfaceParamService.updateById(param);
        return ResponseEntity.ok(Result.success(interfaceParamService.getById(paramId)));
    }

    /**
     * 删除一个参数定义
     */
    @OperationLog(module = "接口管理", operation = "删除接口参数")
    @DeleteMapping("/params/{paramId}")
    public ResponseEntity<Result<Void>> deleteParam(@PathVariable Long paramId) {
        InterfaceParam existing = interfaceParamService.getById(paramId);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "参数定义不存在"));
        }
        interfaceParamService.removeById(paramId);
        return ResponseEntity.ok(Result.success(null));
    }
}
