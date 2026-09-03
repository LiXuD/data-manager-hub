package com.dataplatform.identity.iam.controller;

import com.dataplatform.access.caller.api.feign.CallerInternalFeignClient;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.security.IamAuthorizationService;
import com.dataplatform.identity.iam.service.UserCallerService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import com.dataplatform.identity.security.service.PasswordService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.service.TenantService;
import com.dataplatform.common.util.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 身份租户域用户权限的 User Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserCallerService userCallerService;
    private final UserRoleService userRoleService;
    private final PasswordService passwordService;
    private final IamAuthorizationService authorizationService;
    private final TenantService tenantService;
    private final CallerInternalFeignClient callerInternalFeignClient;

    public UserController(
            UserService userService,
            UserCallerService userCallerService,
            UserRoleService userRoleService,
            PasswordService passwordService,
            IamAuthorizationService authorizationService,
            TenantService tenantService,
            CallerInternalFeignClient callerInternalFeignClient) {
        this.userService = userService;
        this.userCallerService = userCallerService;
        this.userRoleService = userRoleService;
        this.passwordService = passwordService;
        this.authorizationService = authorizationService;
        this.tenantService = tenantService;
        this.callerInternalFeignClient = callerInternalFeignClient;
    }

    @GetMapping("/list")
    public PageResult<User> list(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        authorizationService.requirePermission("user:view");
        return userService.list(
                username, status, authorizationService.tenantFilter(), page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<User>> get(@PathVariable Long id) {
        authorizationService.requirePermission("user:view");
        User user = authorizationService.requireUserInScope(id);
        return ResponseEntity.ok(Result.success(user));
    }

    @OperationLog(module = "用户管理", operation = "新增用户")
    @PostMapping
    public ResponseEntity<Result<User>> create(@RequestBody User user) {
        authorizationService.requirePermission("user:add");
        if (user == null) {
            return badRequest("请求体不能为空");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "用户名不能为空"));
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码不能为空"));
        }

        String username = user.getUsername().trim();
        String password = user.getPassword();
        if (!passwordService.isStrongEnough(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码至少8位，且包含数字和字母"));
        }

        User existing = userService.getByUsername(username);
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, "用户名已存在"));
        }

        user.setId(null);
        user.setUsername(username);
        user.setStatus(CommonStatus.ACTIVE);
        if (!authorizationService.isPlatformAdmin()) {
            Long tenantId = UserContext.getCurrentTenantId();
            if (tenantId == null) {
                return forbidden("TENANT_SCOPE_REQUIRED", "当前用户没有租户作用域");
            }
            user.setTenantId(tenantId);
        } else if (user.getTenantId() != null && !isUsableTenant(user.getTenantId())) {
            return badRequest("租户不存在或未启用");
        }
        user.setDeleted(false);
        user.setLastLoginTime(null);
        user.setCreatedBy(null);
        user.setCreatedAt(null);
        user.setUpdatedBy(null);
        user.setUpdatedAt(null);
        user.setPassword(passwordService.encode(password));
        if (!userService.save(user)) {
            return conflict("用户创建失败，请重试");
        }
        return ResponseEntity.ok(Result.success(user));
    }

    @OperationLog(module = "用户管理", operation = "更新用户")
    @PutMapping("/{id}")
    public ResponseEntity<Result<User>> update(@PathVariable Long id, @RequestBody User user) {
        authorizationService.requirePermission("user:edit");
        if (user == null) {
            return badRequest("请求体不能为空");
        }
        User existing = authorizationService.requireUserInScope(id);
        if (user.getUsername() != null && !user.getUsername().trim().equals(existing.getUsername())) {
            return badRequest("用户名不可修改");
        }
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            String password = user.getPassword();
            if (!passwordService.isStrongEnough(password)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Result.error(400, "密码至少8位，且包含数字和字母"));
            }
            user.setPassword(passwordService.encode(password));
        } else {
            user.setPassword(null);
        }
        user.setId(id);
        user.setUsername(existing.getUsername());
        user.setTenantId(existing.getTenantId());
        user.setStatus(existing.getStatus());
        user.setDeleted(null);
        user.setLastLoginTime(null);
        user.setCreatedBy(null);
        user.setCreatedAt(null);
        user.setUpdatedBy(null);
        user.setUpdatedAt(null);
        if (!userService.updateById(user)) {
            return conflict("用户已被其他请求修改，请刷新后重试");
        }
        authorizationService.invalidateUser(id);
        return ResponseEntity.ok(Result.success(userService.getById(id)));
    }

    @OperationLog(module = "用户管理", operation = "删除用户")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        authorizationService.requirePermission("user:delete");
        authorizationService.requireUserInScope(id);
        authorizationService.forbidCurrentUserMutation(
                id, "SELF_DELETE_FORBIDDEN", "禁止删除当前登录用户");
        if (!userService.removeById(id)) {
            return conflict("用户删除失败，请重试");
        }
        authorizationService.invalidateUser(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "用户管理", operation = "更新用户状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authorizationService.requirePermission("user:edit");
        if (body == null) {
            return badRequest("请求体不能为空");
        }
        String status = body.get("status");

        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "状态不能为空"));
        }
        String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
        CommonStatus statusEnum = CommonStatus.fromCode(normalizedStatus);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "无效的状态值，必须是active或inactive"));
        }

        User existing = authorizationService.requireUserInScope(id);
        authorizationService.forbidCurrentUserMutation(
                id, "SELF_STATUS_MUTATION_FORBIDDEN", "禁止修改当前登录用户状态");
        if (statusEnum.equals(existing.getStatus())) {
            return ResponseEntity.ok(Result.success(null));
        }

        User user = new User();
        user.setId(id);
        user.setStatus(statusEnum);
        if (!userService.updateById(user)) {
            return conflict("用户状态已被其他请求修改，请刷新后重试");
        }
        authorizationService.invalidateUser(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @OperationLog(module = "用户管理", operation = "重置密码")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Result<Void>> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        authorizationService.requirePermission("user:edit");
        if (body == null) {
            return badRequest("请求体不能为空");
        }
        String password = body.get("password");

        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码不能为空"));
        }

        if (!passwordService.isStrongEnough(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "密码至少8位，且包含数字和字母"));
        }

        authorizationService.requireUserInScope(id);

        User user = new User();
        user.setId(id);
        user.setPassword(passwordService.encode(password));
        if (!userService.updateById(user)) {
            return conflict("密码更新失败，请重试");
        }
        authorizationService.invalidateUser(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/{id}/callers")
    public ResponseEntity<Result<List<Long>>> getUserCallers(@PathVariable Long id) {
        authorizationService.requirePermission("user:view");
        authorizationService.requireUserInScope(id);
        List<Long> callerIds = userCallerService.getCallerIdsByUserId(id);
        return ResponseEntity.ok(Result.success(callerIds));
    }

    @OperationLog(module = "用户管理", operation = "关联调用方")
    @PostMapping("/{id}/callers")
    public ResponseEntity<Result<Void>> assignCallers(@PathVariable Long id, @RequestBody List<Long> callerIds) {
        authorizationService.requirePermission("user:edit");
        User target = authorizationService.requireUserInScope(id);
        if (callerIds == null || callerIds.stream().anyMatch(java.util.Objects::isNull)) {
            return badRequest("调用方ID列表不能为空且不能包含空值");
        }
        List<Long> distinctCallerIds = callerIds.stream().distinct().toList();
        if (!distinctCallerIds.isEmpty()) {
            Long tenantId = target.getTenantId();
            if (tenantId == null) {
                return badRequest("租户范围用户才能关联调用方");
            }
            try {
                com.dataplatform.api.Result<List<Long>> validation =
                        callerInternalFeignClient.validate(tenantId, distinctCallerIds);
                if (validation == null || !Integer.valueOf(200).equals(validation.getCode())
                        || validation.getData() == null) {
                    return unavailable("调用方校验服务暂时不可用，请稍后重试");
                }
                Set<Long> usableCallerIds = new HashSet<>(validation.getData());
                if (usableCallerIds.size() != distinctCallerIds.size()
                        || !usableCallerIds.containsAll(distinctCallerIds)) {
                    return badRequest("只能关联当前租户下已启用的调用方");
                }
            } catch (RuntimeException exception) {
                log.warn("Caller ownership validation failed for userId={}", id, exception);
                return unavailable("调用方校验服务暂时不可用，请稍后重试");
            }
        }
        authorizationService.invalidateUser(id);
        userCallerService.assignCallers(id, distinctCallerIds);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<Result<List<Long>>> getUserRoles(@PathVariable Long id) {
        authorizationService.requirePermission("user:view");
        authorizationService.requireUserInScope(id);
        List<Long> roleIds = userRoleService.getRoleIdsByUserId(id);
        return ResponseEntity.ok(Result.success(roleIds));
    }

    @OperationLog(module = "用户管理", operation = "分配角色")
    @PostMapping("/{id}/roles")
    public ResponseEntity<Result<Void>> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        authorizationService.requirePermission("user:edit");
        if (roleIds == null || roleIds.stream().anyMatch(java.util.Objects::isNull)) {
            return badRequest("角色ID列表不能为空且不能包含空值");
        }
        List<Long> distinctRoleIds = roleIds.stream().distinct().toList();
        authorizationService.prepareRoleAssignment(id, distinctRoleIds);
        userRoleService.assignRoles(id, distinctRoleIds);
        return ResponseEntity.ok(Result.success(null));
    }

    private <T> ResponseEntity<Result<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(400, message));
    }

    private <T> ResponseEntity<Result<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error(409, message));
    }

    private <T> ResponseEntity<Result<T>> forbidden(String code, String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(403, code + ": " + message));
    }

    private <T> ResponseEntity<Result<T>> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.error(503, message));
    }

    private boolean isUsableTenant(Long tenantId) {
        TenantInfo tenant = tenantService.getById(tenantId);
        return tenant != null
                && !Boolean.TRUE.equals(tenant.getDeleted())
                && "active".equalsIgnoreCase(tenant.getStatus());
    }
}
