package com.dataplatform.datatype.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.datatype.entity.DataType;
import com.dataplatform.datatype.service.DataTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datatype")
public class DataTypeController {
    @Autowired
    private DataTypeService datatypeService;

    private static final List<String> VALID_STATUSES = List.of("active", "inactive", "pending");

    @GetMapping("/list")
    public PageResult<DataType> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return datatypeService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<DataType>> get(@PathVariable Long id) {
        DataType datatype = datatypeService.getById(id);
        if (datatype == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        return ResponseEntity.ok(Result.success(datatype));
    }

    @PostMapping
    public ResponseEntity<Result<DataType>> create(@RequestBody DataType datatype) {
        // 验证必填参数
        if (datatype.getDataTypeCode() == null || datatype.getDataTypeCode().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "datatypeCode不能为空"));
        }
        // 检查重复
        DataType existing = datatypeService.getByTypeCode(datatype.getDataTypeCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "数据类型编码已存在"));
        }
        datatype.setId(null);
        datatype.setStatus("active");
        datatypeService.save(datatype);
        return ResponseEntity.ok(Result.success(datatype));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<DataType>> update(@PathVariable Long id, @RequestBody DataType datatype) {
        DataType existing = datatypeService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        datatype.setId(id);
        datatypeService.updateById(datatype);
        return ResponseEntity.ok(Result.success(datatypeService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        DataType existing = datatypeService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        datatypeService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        // 验证状态值
        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        DataType existing = datatypeService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }

        DataType datatype = new DataType();
        datatype.setId(id);
        datatype.setStatus(status);
        datatypeService.updateById(datatype);
        return ResponseEntity.ok(Result.success(null));
    }
}
