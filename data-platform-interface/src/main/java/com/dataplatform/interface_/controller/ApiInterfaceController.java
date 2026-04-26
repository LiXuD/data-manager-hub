package com.dataplatform.interface_.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.service.ApiInterfaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interface")
public class ApiInterfaceController {

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    private static final List<String> VALID_STATUSES = List.of("active", "inactive");

    @GetMapping("/list")
    public PageResult<ApiInterface> list(
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
            apiInterface.setStatus("active");
        }
        if (apiInterface.getSort() == null) {
            apiInterface.setSort(0);
        }
        apiInterfaceService.save(apiInterface);
        return ResponseEntity.ok(Result.success(apiInterface));
    }

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

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        ApiInterface existing = apiInterfaceService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "接口不存在"));
        }

        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(id);
        apiInterface.setStatus(status);
        apiInterfaceService.updateById(apiInterface);
        return ResponseEntity.ok(Result.success(null));
    }
}
