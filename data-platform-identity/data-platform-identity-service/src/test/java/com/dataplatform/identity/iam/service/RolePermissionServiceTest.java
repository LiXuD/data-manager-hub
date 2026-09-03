package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.identity.iam.entity.RolePermission;
import com.dataplatform.identity.iam.security.IamAuthorizationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class RolePermissionServiceTest {

    @Test
    void convertsBatchPersistenceFailureToStructuredConflict() {
        RolePermissionService service = spy(new RolePermissionService());
        doReturn(true).when(service).remove(any(LambdaQueryWrapper.class));
        doReturn(false).when(service).saveBatch(any(List.class));

        IamAuthorizationException exception = assertThrows(
                IamAuthorizationException.class,
                () -> service.assignPermissions(7L, List.of(11L)));

        assertEquals("ROLE_PERMISSION_CREATE_FAILED", exception.getErrorCode());
    }
}
