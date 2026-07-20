package com.dataplatform.identity.iam.controller;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.service.UserCallerService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import com.dataplatform.identity.security.service.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 身份租户域用户权限的 User Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private UserCallerService userCallerService;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private PasswordService passwordService;

    @GetMapping("/list")
    public PageResult<User> list(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        return userService.list(username, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<User>> get(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        return ResponseEntity.ok(Result.success(user));
    }

    @OperationLog(module = "用户管理", operation = "新增用户")
    @PostMapping
    public ResponseEntity<Result<User>> create(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "用户名不能为空"));
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码不能为空"));
        }

        String password = user.getPassword();
        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码至少8位，且包含数字和字母"));
        }

        User existing = userService.getByUsername(user.getUsername());
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "用户名已存在"));
        }

        user.setId(null);
        user.setStatus(CommonStatus.ACTIVE);
        user.setPassword(passwordService.encode(password));
        userService.save(user);
        return ResponseEntity.ok(Result.success(user));
    }

    @OperationLog(module = "用户管理", operation = "更新用户")
    @PutMapping("/{id}")
    public ResponseEntity<Result<User>> update(@PathVariable Long id, @RequestBody User user) {
        User existing = userService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        if (user.getPassword() != null) {
            String password = user.getPassword();
            if (password.length() < 8 || !password.matches(".*[A-Za-z].*")
                    || !password.matches(".*\\d.*")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Result.error(400, "密码至少8位，且包含数字和字母"));
            }
            user.setPassword(passwordService.encode(password));
        }
        user.setId(id);
        userService.updateById(user);
        return ResponseEntity.ok(Result.success(userService.getById(id)));
    }

    @OperationLog(module = "用户管理", operation = "删除用户")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        User existing = userService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        userService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "用户管理", operation = "更新用户状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }
        if (!status.equals("active") && !status.equals("inactive") && !status.equals("locked")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值"));
        }

        User existing = userService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }

        User user = new User();
        user.setId(id);
        user.setStatus(CommonStatus.fromCode(status));
        userService.updateById(user);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "用户管理", operation = "重置密码")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Result<Void>> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");

        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码不能为空"));
        }

        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码至少8位，且包含数字和字母"));
        }

        User existing = userService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }

        User user = new User();
        user.setId(id);
        user.setPassword(passwordService.encode(password));
        userService.updateById(user);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/{id}/callers")
    public ResponseEntity<Result<List<Long>>> getUserCallers(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        List<Long> callerIds = userCallerService.getCallerIdsByUserId(id);
        return ResponseEntity.ok(Result.success(callerIds));
    }

    @OperationLog(module = "用户管理", operation = "关联调用方")
    @PostMapping("/{id}/callers")
    public ResponseEntity<Result<Void>> assignCallers(@PathVariable Long id, @RequestBody List<Long> callerIds) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        userCallerService.assignCallers(id, callerIds);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<Result<List<Long>>> getUserRoles(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        List<Long> roleIds = userRoleService.getRoleIdsByUserId(id);
        return ResponseEntity.ok(Result.success(roleIds));
    }

    @OperationLog(module = "用户管理", operation = "分配角色")
    @PostMapping("/{id}/roles")
    public ResponseEntity<Result<Void>> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "用户不存在"));
        }
        userRoleService.assignRoles(id, roleIds);
        return ResponseEntity.ok(Result.success(null));
    }
}
