package com.dataplatform.identity.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.api.Result;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.identity.api.dto.EncryptionReqDTO;
import com.dataplatform.identity.api.dto.LoginReqDTO;
import com.dataplatform.identity.api.dto.LoginRespDTO;
import com.dataplatform.identity.api.dto.RoleDTO;
import com.dataplatform.identity.api.dto.TenantDTO;
import com.dataplatform.identity.api.dto.UserDTO;
import com.dataplatform.identity.api.feign.IdentityFeignClient;
import com.dataplatform.identity.iam.entity.Permission;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.mapper.PermissionMapper;
import com.dataplatform.identity.iam.mapper.RoleMapper;
import com.dataplatform.identity.iam.mapper.UserRoleMapper;
import com.dataplatform.identity.iam.service.RolePermissionService;
import com.dataplatform.identity.iam.service.RoleService;
import com.dataplatform.identity.iam.service.UserService;
import com.dataplatform.identity.security.service.EncryptionService;
import com.dataplatform.identity.tenant.entity.TenantInfo;
import com.dataplatform.identity.tenant.service.TenantService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 身份租户域的 Identity Contract Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/identity")
public class IdentityContractController implements IdentityFeignClient {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private EncryptionService encryptionService;

    @Override
    public Result<TenantDTO> getTenant(Long id) {
        return Result.success(toTenantDTO(tenantService.getById(id)));
    }

    @Override
    public Result<UserDTO> getUser(Long id) {
        return Result.success(toUserDTO(userService.getById(id)));
    }

    @Override
    public Result<UserDTO> getUserByUsername(String username) {
        return Result.success(toUserDTO(userService.getByUsername(username)));
    }

    @Override
    public Result<RoleDTO> getRole(Long id) {
        return Result.success(toRoleDTO(roleService.getById(id)));
    }

    @Override
    public Result<LoginRespDTO> login(LoginReqDTO dto) {
        User user = userService.getByUsername(dto.getUsername());
        if (user == null || !dto.getPassword().equals(user.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        List<String> permissionCodes = getUserPermissions(user.getId());
        List<String> roleCodes = getUserRoles(user.getId());
        UserContext.login(user.getId(), user.getUsername(), user.getTenantId(), permissionCodes);

        LoginRespDTO resp = new LoginRespDTO();
        resp.setToken(StpUtil.getTokenValue());
        resp.setUserId(user.getId());
        resp.setTenantId(user.getTenantId());
        resp.setUsername(user.getUsername());
        resp.setRoles(roleCodes);
        resp.setPermissions(permissionCodes);
        return Result.success(resp);
    }

    @Override
    public Result<String> encrypt(EncryptionReqDTO dto) {
        return Result.success(encryptionService.encrypt(dto.getText(), dto.getTableName()));
    }

    @Override
    public Result<String> decrypt(EncryptionReqDTO dto) {
        return Result.success(encryptionService.decrypt(dto.getText(), dto.getTableName()));
    }

    private TenantDTO toTenantDTO(TenantInfo entity) {
        if (entity == null) {
            return null;
        }
        TenantDTO dto = new TenantDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private UserDTO toUserDTO(User entity) {
        if (entity == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setRealName(entity.getRealName());
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private RoleDTO toRoleDTO(Role entity) {
        if (entity == null) {
            return null;
        }
        RoleDTO dto = new RoleDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private List<String> getUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
    }

    private List<String> getUserPermissions(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .flatMap(roleId -> rolePermissionService.getPermissionIdsByRoleId(roleId).stream())
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toList());
    }
}
