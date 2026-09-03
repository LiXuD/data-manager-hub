package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.identity.iam.entity.UserCaller;
import com.dataplatform.identity.iam.security.IamAuthorizationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class UserCallerServiceTest {

    @Test
    void convertsBatchPersistenceFailureToStructuredConflict() {
        UserCallerService service = spy(new UserCallerService());
        doReturn(true).when(service).remove(any(LambdaQueryWrapper.class));
        doReturn(false).when(service).saveBatch(any(List.class));

        IamAuthorizationException exception = assertThrows(
                IamAuthorizationException.class,
                () -> service.assignCallers(7L, List.of(11L)));

        assertEquals("USER_CALLER_CREATE_FAILED", exception.getErrorCode());
    }
}
