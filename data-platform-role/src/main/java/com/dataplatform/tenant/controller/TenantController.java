package com.dataplatform.role.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.role.entity.RoleInfo;
import com.dataplatform.role.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    public PageResult<RoleInfo> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        
        Page<RoleInfo> pageResult = roleService.listPage(page, pageSize, keyword, status);
        
        PageResult<RoleInfo> result = new PageResult<>();
        result.setCode(0);  // Important: explicitly set code to 0
        result.setMessage("success");
        result.setData(pageResult.getRecords());
        result.setTotal(pageResult.getTotal());
        result.setPage(page);
        result.setPageSize(pageSize);
        
        return result;
    }

    @GetMapping("/{id}")
    public Result<RoleInfo> getById(@PathVariable(name = "id") Long id) {
        RoleInfo role = roleService.getById(id);
        return Result.success(role);
    }

    @PostMapping
    public Result<RoleInfo> create(@RequestBody RoleInfo role) {
        role.setId(null);
        roleService.save(role);
        return Result.success(role);
    }

    @PutMapping("/{id}")
    public Result<RoleInfo> update(@PathVariable(name = "id") Long id, @RequestBody RoleInfo role) {
        role.setId(id);
        roleService.updateById(role);
        return Result.success(roleService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable(name = "id") Long id) {
        roleService.removeById(id);
        return Result.success(null);
    }
}
