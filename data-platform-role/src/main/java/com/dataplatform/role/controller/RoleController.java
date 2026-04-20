package com.dataplatform.role.controller;

import com.dataplatform.common.pojo.ApiResponse;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.role.entity.Role;
import com.dataplatform.role.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/role")
public class RoleController {
    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    public ApiResponse<PageResponse<Role>> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<Role> result = roleService.list(roleName, status, page, pageSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Role> get(@PathVariable Long id) {
        return ApiResponse.success(roleService.getById(id));
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody Role role) {
        roleService.create(role);
        return ApiResponse.success(null);
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody Role role) {
        roleService.update(role);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.success(null);
    }
}