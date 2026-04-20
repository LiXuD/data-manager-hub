package com.dataplatform.datatype.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.datatype.entity.DataTypeInfo;
import com.dataplatform.datatype.service.DataTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/datatype")
public class DataTypeController {

    @Autowired
    private DataTypeService datatypeService;

    @GetMapping("/list")
    public PageResult<DataTypeInfo> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        
        Page<DataTypeInfo> pageResult = datatypeService.listPage(page, pageSize, keyword, status);
        
        PageResult<DataTypeInfo> result = new PageResult<>();
        result.setCode(0);  // Important: explicitly set code to 0
        result.setMessage("success");
        result.setData(pageResult.getRecords());
        result.setTotal(pageResult.getTotal());
        result.setPage(page);
        result.setPageSize(pageSize);
        
        return result;
    }

    @GetMapping("/{id}")
    public Result<DataTypeInfo> getById(@PathVariable(name = "id") Long id) {
        DataTypeInfo datatype = datatypeService.getById(id);
        return Result.success(datatype);
    }

    @PostMapping
    public Result<DataTypeInfo> create(@RequestBody DataTypeInfo datatype) {
        datatype.setId(null);
        datatypeService.save(datatype);
        return Result.success(datatype);
    }

    @PutMapping("/{id}")
    public Result<DataTypeInfo> update(@PathVariable(name = "id") Long id, @RequestBody DataTypeInfo datatype) {
        datatype.setId(id);
        datatypeService.updateById(datatype);
        return Result.success(datatypeService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable(name = "id") Long id) {
        datatypeService.removeById(id);
        return Result.success(null);
    }
}
