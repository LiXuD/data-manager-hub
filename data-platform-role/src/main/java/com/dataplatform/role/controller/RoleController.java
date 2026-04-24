package com.dataplatform.role.controller;

import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.role.entity.Role;
import com.dataplatform.role.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
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
    public ResponseEntity<Result<Role>> get(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        return ResponseEntity.ok(Result.success(role));
    }

    @PostMapping
    public ResponseEntity<Result<Role>> create(@RequestBody Role role) {
        // 校验必填字段
        if (role.getRoleCode() == null || role.getRoleCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "角色代码不能为空"));
        }
        if (role.getRoleName() == null || role.getRoleName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "角色名称不能为空"));
        }

        // 检查重复
        Role existing = roleService.getByRoleCode(role.getRoleCode());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "角色代码已存在"));
        }

        role.setId(null);
        role.setStatus("active");
        roleService.save(role);
        return ResponseEntity.ok(Result.success(role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result<Role>> update(@PathVariable Long id, @RequestBody Role role) {
        // 检查是否存在
        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        role.setId(id);
        roleService.updateById(role);
        return ResponseEntity.ok(Result.success(roleService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        // 检查是否存在
        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }
        roleService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        // 校验status有效性
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }

        List<String> validStatuses = Arrays.asList("active", "inactive");
        if (!validStatuses.contains(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值"));
        }

        // 检查是否存在
        Role existing = roleService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "角色不存在"));
        }

        Role role = new Role();
        role.setId(id);
        role.setStatus(status);
        roleService.updateById(role);
        return ResponseEntity.ok(Result.success(null));
    }
}