package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.identity.iam.entity.UserRole;
import com.dataplatform.identity.iam.security.IamAuthorizationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class UserRoleServiceTest {

    @Test
    void convertsBatchPersistenceFailureToStructuredConflict() {
        UserRoleService service = spy(new UserRoleService());
        doReturn(true).when(service).remove(any(LambdaQueryWrapper.class));
        doReturn(false).when(service).saveBatch(any(List.class));

        IamAuthorizationException exception = assertThrows(
                IamAuthorizationException.class,
                () -> service.assignRoles(7L, List.of(11L)));

        assertEquals("USER_ROLE_CREATE_FAILED", exception.getErrorCode());
    }
}
