package com.dataplatform.identity.iam.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
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
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return Result.error(400, "用户名和密码不能为空");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getStatus, "active"));

        if (user == null || !passwordService.matches(password, user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        if (!passwordService.isEncoded(user.getPassword())) {
            User passwordUpgrade = new User();
            passwordUpgrade.setId(user.getId());
            passwordUpgrade.setPassword(passwordService.encode(password));
            userMapper.updateById(passwordUpgrade);
        }

        List<String> permissionCodes = getUserPermissions(user.getId());
        List<String> roleCodes = getUserRoles(user.getId());

        UserContext.login(user.getId(), user.getUsername(), user.getTenantId(), permissionCodes);

        User loginUpdate = new User();
        loginUpdate.setId(user.getId());
        loginUpdate.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(loginUpdate);

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

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("userId", UserContext.getCurrentUserId());
        data.put("username", UserContext.getCurrentUsername());

        return Result.success(data);
    }

    @GetMapping("/userinfo")
    public Result<Map<String, Object>> getUserInfo() {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }

        User user = userMapper.selectById(UserContext.getCurrentUserId());
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        TenantInfo tenant = user.getTenantId() == null ? null : tenantMapper.selectById(user.getTenantId());

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
        data.put("permissions", getUserPermissions(user.getId()));

        return Result.success(data);
    }

    @OperationLog(module = "个人中心", operation = "更新个人信息")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }
        User update = new User();
        update.setId(UserContext.getCurrentUserId());
        update.setNickname(body.get("nickname"));
        update.setEmail(body.get("email"));
        update.setPhone(body.get("phone"));
        userMapper.updateById(update);
        return getUserInfo();
    }

    @OperationLog(module = "个人中心", operation = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        if (!UserContext.isLoggedIn()) {
            return Result.error(401, "未登录");
        }
        User user = userMapper.selectById(UserContext.getCurrentUserId());
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (user == null || oldPassword == null || !passwordService.matches(oldPassword, user.getPassword())) {
            return Result.error(400, "当前密码错误");
        }
        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            return Result.error(400, "新密码至少8位，且包含数字和字母");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordService.encode(newPassword));
        userMapper.updateById(update);
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
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
    }

    private List<String> getUserPermissions(Long userId) {
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(userRoleWrapper);
        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());

        List<Long> permissionIds = roleIds.stream()
                .flatMap(rid -> rolePermissionService.getPermissionIdsByRoleId(rid).stream())
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
        return permissions.stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toList());
    }
}
