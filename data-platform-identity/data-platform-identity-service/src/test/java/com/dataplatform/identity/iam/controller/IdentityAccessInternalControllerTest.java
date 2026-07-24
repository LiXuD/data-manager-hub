package com.dataplatform.identity.iam.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.identity.iam.entity.Role;
import com.dataplatform.identity.iam.entity.User;
import com.dataplatform.identity.iam.service.RoleService;
import com.dataplatform.identity.iam.service.UserCallerService;
import com.dataplatform.identity.iam.service.UserRoleService;
import com.dataplatform.identity.iam.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class IdentityAccessInternalControllerTest {

    @Test
    void exposesIdentityAccessContractUnderInternalPath() {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                IdentityAccessInternalController.class, RequestMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/internal/v1/identity/users"}, mapping.value());
    }

    @Test
    void returnsCanonicalActiveRoleCodesOnly() {
        UserService userService = mock(UserService.class);
        UserCallerService userCallerService = mock(UserCallerService.class);
        UserRoleService userRoleService = mock(UserRoleService.class);
        RoleService roleService = mock(RoleService.class);
        IdentityAccessInternalController controller = new IdentityAccessInternalController(
                userService, userCallerService, userRoleService, roleService);
        User user = new User();
        user.setId(1L);
        user.setStatus(CommonStatus.ACTIVE);
        user.setDeleted(false);
        when(userService.getById(1L)).thenReturn(user);
        when(userRoleService.getRoleIdsByUserId(1L)).thenReturn(List.of(10L, 11L));
        Role active = role(10L, "ADMIN", CommonStatus.ACTIVE, false);
        Role inactive = role(11L, "OLD_ADMIN", CommonStatus.INACTIVE, false);
        when(roleService.listByIds(List.of(10L, 11L)))
                .thenReturn(List.of(active, inactive));

        assertThat(controller.getRoleCodes(1L).getData()).containsExactly("admin");
    }

    private Role role(Long id, String code, CommonStatus status, boolean deleted) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(status);
        role.setDeleted(deleted);
        return role;
    }
}
