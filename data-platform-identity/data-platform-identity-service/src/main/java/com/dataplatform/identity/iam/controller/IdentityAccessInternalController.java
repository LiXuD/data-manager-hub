package com.dataplatform.identity.iam.controller;

import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.security.InternalScope;
import com.dataplatform.common.security.RoleCodeNormalizer;
import com.dataplatform.identity.api.dto.CallerAccessDTO;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.service.RoleService;
import com.dataplatform.identity.iam.service.UserCallerService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 身份域用户数据范围与角色的内部只读接口。
 */
@RestController
@RequestMapping("/internal/v1/identity/users")
@InternalScope("identity:access:read")
public class IdentityAccessInternalController implements IdentityAccessInternalFeignClient {

    private final UserService userService;
    private final UserCallerService userCallerService;
    private final UserRoleService userRoleService;
    private final RoleService roleService;

    public IdentityAccessInternalController(
            UserService userService,
            UserCallerService userCallerService,
            UserRoleService userRoleService,
            RoleService roleService) {
        this.userService = userService;
        this.userCallerService = userCallerService;
        this.userRoleService = userRoleService;
        this.roleService = roleService;
    }

    @Override
    public Result<CallerAccessDTO> getCallerAccess(Long userId, Long callerId) {
        User user = userService.getById(userId);
        CallerAccessDTO response = new CallerAccessDTO();
        response.setUserId(userId);
        response.setCallerId(callerId);
        response.setTenantId(user != null ? user.getTenantId() : null);
        response.setAllowed(user != null
                && Boolean.FALSE.equals(user.getDeleted())
                && CommonStatus.ACTIVE.equals(user.getStatus())
                && userCallerService.getCallerIdsByUserId(userId).contains(callerId));
        return Result.success(response);
    }

    @Override
    public Result<List<Long>> getCallerIds(Long userId) {
        User user = userService.getById(userId);
        if (user == null
                || !Boolean.FALSE.equals(user.getDeleted())
                || !CommonStatus.ACTIVE.equals(user.getStatus())) {
            return Result.success(List.of());
        }
        return Result.success(userCallerService.getCallerIdsByUserId(userId));
    }

    @Override
    public Result<List<String>> getRoleCodes(Long userId) {
        List<Long> roleIds = userRoleService.getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<String> roleCodes = roleService.listByIds(roleIds).stream()
                .filter(role -> Boolean.FALSE.equals(role.getDeleted()))
                .filter(role -> CommonStatus.ACTIVE.equals(role.getStatus()))
                .map(Role::getRoleCode)
                .map(RoleCodeNormalizer::normalize)
                .filter(java.util.Objects::nonNull)
                .toList();
        return Result.success(roleCodes);
    }
}
