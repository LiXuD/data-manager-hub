package com.dataplatform.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.vendor.entity.DataType;
import com.dataplatform.vendor.mapper.DataTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data-type")
public class DataTypeController {

    @Autowired
    private DataTypeMapper dataTypeMapper;

    private static final List<String> VALID_STATUSES = List.of("active", "inactive", "pending");

    @GetMapping("/list")
    public PageResult<DataType> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(DataType::getDataTypeName, keyword)
                .or()
                .like(DataType::getDataTypeCode, keyword)
            );
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DataType::getStatus, status);
        }
        wrapper.eq(DataType::getDeleted, false);
        wrapper.orderByDesc(DataType::getCreatedAt);

        Page<DataType> result = dataTypeMapper.selectPage(new Page<>(page, pageSize), wrapper);

        PageResult<DataType> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<DataType>> get(@PathVariable Long id) {
        DataType dataType = dataTypeMapper.selectById(id);
        if (dataType == null || Boolean.TRUE.equals(dataType.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        return ResponseEntity.ok(Result.success(dataType));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Result<DataType>> getByCode(@PathVariable String code) {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataType::getDataTypeCode, code);
        wrapper.eq(DataType::getDeleted, false);
        DataType dataType = dataTypeMapper.selectOne(wrapper);
        if (dataType == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        return ResponseEntity.ok(Result.success(dataType));
    }

    @PostMapping
    public ResponseEntity<Result<DataType>> create(@RequestBody DataType dataType) {
        if (dataType.getDataTypeCode() == null || dataType.getDataTypeCode().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "dataTypeCode不能为空"));
        }
        
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataType::getDataTypeCode, dataType.getDataTypeCode());
        wrapper.eq(DataType::getDeleted, false);
        DataType existing = dataTypeMapper.selectOne(wrapper);
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "数据类型编码已存在"));
        }
        
        dataType.setId(null);
        if (dataType.getStatus() == null) {
            dataType.setStatus("active");
        }
        dataType.setDeleted(false);
        dataType.setCreatedAt(LocalDateTime.now());
        dataType.setUpdatedAt(LocalDateTime.now());
        dataTypeMapper.insert(dataType);
        return ResponseEntity.ok(Result.success(dataType));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<DataType>> update(@PathVariable Long id, @RequestBody DataType dataType) {
        DataType existing = dataTypeMapper.selectById(id);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        dataType.setId(id);
        dataType.setUpdatedAt(LocalDateTime.now());
        dataTypeMapper.updateById(dataType);
        return ResponseEntity.ok(Result.success(dataTypeMapper.selectById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        DataType existing = dataTypeMapper.selectById(id);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }
        DataType dataType = new DataType();
        dataType.setId(id);
        dataType.setDeleted(true);
        dataTypeMapper.updateById(dataType);
        return ResponseEntity.ok(Result.success(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: " + VALID_STATUSES));
        }

        DataType existing = dataTypeMapper.selectById(id);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "数据类型不存在"));
        }

        DataType dataType = new DataType();
        dataType.setId(id);
        dataType.setStatus(status);
        dataType.setUpdatedAt(LocalDateTime.now());
        dataTypeMapper.updateById(dataType);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/all")
    public ResponseEntity<Result<List<DataType>>> listAll() {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataType::getStatus, "active");
        wrapper.eq(DataType::getDeleted, false);
        wrapper.orderByAsc(DataType::getDataTypeCode);
        List<DataType> list = dataTypeMapper.selectList(wrapper);
        return ResponseEntity.ok(Result.success(list));
    }
}
