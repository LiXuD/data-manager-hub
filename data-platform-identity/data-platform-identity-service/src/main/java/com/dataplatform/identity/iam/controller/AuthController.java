package com.dataplatform.identity.iam.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.security.RoleCodeNormalizer;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.mapper.PermissionMapper;
import com.dataplatform.identity.iam.mapper.RoleMapper;
import com.dataplatform.identity.iam.mapper.UserMapper;
import com.dataplatform.identity.iam.mapper.UserRoleMapper;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.mapper.TenantMapper;
import com.dataplatform.identity.security.service.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * 身份租户域用户权限的 Auth Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RolePermissionService rolePermissionService;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private TenantMapper tenantMapper;
    @Autowired
    private PasswordService passwordService;

    @OperationLog(module = "认证管理", operation = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        if (credentials == null) {
            return Result.error(400, "请求体不能为空");
        }
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getStatus, "active")
                .eq(User::getDeleted, false));

        if (user == null || Boolean.TRUE.equals(user.getDeleted())
                || !passwordService.matches(password, user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }
        if (!isUsableTenant(user.getTenantId())) {
            return Result.error(401, "用户名或密码错误");
        }

        if (!passwordService.isEncoded(user.getPassword())) {
            User passwordUpgrade = new User();
            passwordUpgrade.setId(user.getId());
            passwordUpgrade.setPassword(passwordService.encode(password));
            if (userMapper.updateById(passwordUpgrade) <= 0) {
                return Result.error(409, "密码升级失败，请重试");
            }
        }

        List<String> permissionCodes = getUserPermissions(user.getId());
        List<String> roleCodes = getUserRoles(user.getId());

        User loginUpdate = new User();
        loginUpdate.setId(user.getId());
        loginUpdate.setLastLoginTime(LocalDateTime.now());
        if (userMapper.updateById(loginUpdate) <= 0) {
            return Result.error(409, "登录状态更新失败，请重试");
        }

        UserContext.login(user.getId(), user.getUsername(), user.getTenantId(), permissionCodes);

        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("username", user.getUsername());
        data.put("userId", user.getId());
        data.put("tenantId", user.getTenantId());
        data.put("permissions", permissionCodes);
        data.put("roles", roleCodes);

        return Result.success(data);
    }

    @OperationLog(module = "认证管理", operation = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        UserContext.logout();
        return Result.success(null);
    }

    @GetMapping("/verify")
    public Result<Map<String, Object>> verify() {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录或会话已过期");
        }

        Long userId = UserContext.getCurrentUserId();
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !CommonStatus.ACTIVE.equals(user.getStatus())
                || Boolean.TRUE.equals(user.getDeleted()) || !isUsableTenant(user.getTenantId())) {
            UserContext.logout();
            return Result.error(401, "未登录或会话已过期");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());

        return Result.success(data);
    }

    @GetMapping("/userinfo")
    public Result<Map<String, Object>> getUserInfo() {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }

        User user = userMapper.selectById(UserContext.getCurrentUserId());
        if (user == null || !CommonStatus.ACTIVE.equals(user.getStatus())
                || Boolean.TRUE.equals(user.getDeleted())) {
            UserContext.logout();
            return Result.error(401, "会话已失效");
        }
        TenantInfo tenant = user.getTenantId() == null ? null : tenantMapper.selectById(user.getTenantId());
        if (user.getTenantId() != null && !isUsableTenant(tenant)) {
            UserContext.logout();
            return Result.error(401, "会话已失效");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("tenantId", user.getTenantId());
        data.put("tenantName", tenant == null ? null : tenant.getTenantName());
        data.put("lastLoginTime", user.getLastLoginTime());
        data.put("roles", getUserRoles(user.getId()));
        List<String> permissions = getUserPermissions(user.getId());
        // Permissions are cached in the shared Sa-Token session at login time. Refresh the
        // snapshot here as well so permissions added by a migration or role update are visible
        // to downstream services without requiring a new token.
        StpUtil.getSession().set(UserContext.PERMISSIONS_KEY, permissions);
        data.put("permissions", permissions);

        return Result.success(data);
    }

    private boolean isUsableTenant(Long tenantId) {
        return tenantId == null || isUsableTenant(tenantMapper.selectById(tenantId));
    }

    private boolean isUsableTenant(TenantInfo tenant) {
        return tenant != null && !Boolean.TRUE.equals(tenant.getDeleted())
                && "active".equalsIgnoreCase(tenant.getStatus());
    }

    @OperationLog(module = "个人中心", operation = "更新个人信息")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }
        if (body == null) {
            return Result.error(400, "请求体不能为空");
        }
        User update = new User();
        update.setId(UserContext.getCurrentUserId());
        update.setNickname(body.get("nickname"));
        update.setEmail(body.get("email"));
        update.setPhone(body.get("phone"));
        if (userMapper.updateById(update) <= 0) {
            return Result.error(409, "个人信息已被其他请求修改，请重试");
        }
        return getUserInfo();
    }

    @OperationLog(module = "个人中心", operation = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }
        if (body == null) {
            return Result.error(400, "请求体不能为空");
        }
        User user = userMapper.selectById(UserContext.getCurrentUserId());
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (user == null || oldPassword == null || !passwordService.matches(oldPassword, user.getPassword())) {
            return Result.error(400, "当前密码错误");
        }
        if (!passwordService.isStrongEnough(newPassword)) {
            return Result.error(400, "新密码至少8位，且包含数字和字母");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordService.encode(newPassword));
        if (userMapper.updateById(update) <= 0) {
            return Result.error(409, "密码已被其他请求修改，请重试");
        }
        StpUtil.logout(user.getId());
        return Result.success(null);
    }

    private List<String> getUserRoles(Long userId) {
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(userRoleWrapper);
        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());

        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream()
                .filter(role -> Boolean.FALSE.equals(role.getDeleted()))
                .filter(role -> CommonStatus.ACTIVE.equals(role.getStatus()))
                .map(Role::getRoleCode)
                .map(RoleCodeNormalizer::normalize)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> getUserPermissions(Long userId) {
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(userRoleWrapper);
        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<Long> assignedRoleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        List<Long> roleIds = roleMapper.selectBatchIds(assignedRoleIds).stream()
                .filter(role -> Boolean.FALSE.equals(role.getDeleted()))
                .filter(role -> CommonStatus.ACTIVE.equals(role.getStatus()))
                .map(Role::getId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        List<Long> permissionIds = roleIds.stream()
                .flatMap(rid -> rolePermissionService.getPermissionIdsByRoleId(rid).stream())
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
        return permissions.stream()
                .filter(permission -> "active".equalsIgnoreCase(permission.getStatus()))
                .filter(permission -> Boolean.FALSE.equals(permission.getDeleted()))
                .map(Permission::getPermissionCode)
                .collect(Collectors.toList());
    }
}
