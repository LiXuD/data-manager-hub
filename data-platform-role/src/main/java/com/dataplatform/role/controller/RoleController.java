package com.dataplatform.role.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.role.entity.Role;
import com.dataplatform.role.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/role")
public class RoleController {
    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    public PageResult<Role> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return roleService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public Result<Role> get(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return Result.fail(404, "角色不存在");
        }
        return Result.success(role);
    }

    @PostMapping
    public Result<Role> create(@RequestBody Role role) {
        role.setId(null);
        role.setStatus("active");
        roleService.save(role);
        return Result.success(role);
    }

    @PutMapping("/{id}")
    public Result<Role> update(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        roleService.updateById(role);
        return Result.success(roleService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        Role role = new Role();
        role.setId(id);
        role.setStatus(status);
        roleService.updateById(role);
        return Result.success(null);
    }
}