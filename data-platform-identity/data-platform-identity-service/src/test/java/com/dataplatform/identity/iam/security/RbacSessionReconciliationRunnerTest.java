package com.dataplatform.identity.iam.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RbacSessionReconciliationRunnerTest {

    @Test
    void invalidatesAllExistingSessionsOnceForSecurityRevision() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(RbacSessionReconciliationRunner.REVISION_KEY)).thenReturn("v026");

        new RbacSessionReconciliationRunner(redisTemplate, authorizationService).run(null);

        verify(authorizationService).invalidateAllUsers();
        verify(values).set(
                RbacSessionReconciliationRunner.REVISION_KEY,
                RbacSessionReconciliationRunner.REVISION);
    }

    @Test
    void keepsSessionsWhenRevisionWasAlreadyReconciled() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        IamAuthorizationService authorizationService = mock(IamAuthorizationService.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(RbacSessionReconciliationRunner.REVISION_KEY))
                .thenReturn(RbacSessionReconciliationRunner.REVISION);

        new RbacSessionReconciliationRunner(redisTemplate, authorizationService).run(null);

        verify(authorizationService, never()).invalidateAllUsers();
    }
}
