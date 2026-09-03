package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.mapper.UserRoleMapper;
import com.dataplatform.identity.iam.security.IamAuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 身份租户域用户权限的 User Role Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class UserRoleService extends ServiceImpl<UserRoleMapper, UserRole> {

    public List<Long> getRoleIdsByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
    }

    public List<Long> getUserIdsByRoleId(Long roleId) {
        return this.list(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, roleId))
                .stream()
                .map(UserRole::getUserId)
                .distinct()
                .toList();
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        // A zero-row delete is a valid first assignment/clear operation, so its
        // boolean result cannot be used as a persistence-failure signal.
        this.remove(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> {
                        UserRole userRole = new UserRole();
                        userRole.setUserId(userId);
                        userRole.setRoleId(roleId);
                        return userRole;
                    })
                    .toList();
            if (!this.saveBatch(userRoles)) {
                throw assignmentConflict("USER_ROLE_CREATE_FAILED", "用户角色关联更新失败，请重试");
            }
        }
    }

    private IamAuthorizationException assignmentConflict(String code, String message) {
        return new IamAuthorizationException(HttpStatus.CONFLICT, code, message);
    }
}
