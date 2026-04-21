package com.dataplatform.datatype.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.datatype.entity.DataType;
import com.dataplatform.datatype.service.DataTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/datatype")
public class DataTypeController {
    @Autowired
    private DataTypeService datatypeService;

    @GetMapping("/list")
    public PageResult<DataType> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return datatypeService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<DataType> get(@PathVariable Long id) {
        DataType datatype = datatypeService.getById(id);
        if (datatype == null) {
            return Result.fail(404, "数据类型不存在");
        }
        return Result.success(datatype);
    }

    @PostMapping
    public Result<DataType> create(@RequestBody DataType datatype) {
        datatype.setId(null);
        datatype.setStatus("active");
        datatypeService.save(datatype);
        return Result.success(datatype);
    }

    @PutMapping("/{id}")
    public Result<DataType> update(@PathVariable Long id, @RequestBody DataType datatype) {
        datatype.setId(id);
        datatypeService.updateById(datatype);
        return Result.success(datatypeService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        datatypeService.removeById(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        DataType datatype = new DataType();
        datatype.setId(id);
        datatype.setStatus(status);
        datatypeService.updateById(datatype);
        return Result.success(null);
    }
}