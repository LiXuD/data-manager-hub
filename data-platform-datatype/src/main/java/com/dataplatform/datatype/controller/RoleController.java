package com.dataplatform.datatype.controller;

import com.dataplatform.common.pojo.ApiResponse;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.datatype.entity.DataType;
import com.dataplatform.datatype.service.DataTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/datatype")
public class DataTypeController {
    @Autowired
    private DataTypeService datatypeService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<DataType>> list(
            @RequestParam(required = false) String datatypeName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<DataType> result = datatypeService.list(datatypeName, status, page, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<DataType> get(@PathVariable Long id) {
        return ApiResponse.success(datatypeService.getById(id));
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody DataType datatype) {
        datatypeService.create(datatype);
        return ApiResponse.success(null);
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody DataType datatype) {
        datatypeService.update(datatype);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        datatypeService.delete(id);
        return ApiResponse.success(null);
    }
}