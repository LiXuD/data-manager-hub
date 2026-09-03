package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.domain.GrantSource;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ApiKeyInterfaceServiceTest {

    @Test
    void rejectsBatchWriteFailureAfterReplacingInterfaceGrants() {
        ApiKeyInterfaceService service = spy(new ApiKeyInterfaceService());
        doReturn(true).when(service).remove(any());
        doReturn(false).when(service).saveBatch(anyList());

        assertThatThrownBy(() -> service.assignInterfaces(7L, List.of(11L)))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo("API_KEY_INTERFACE_ASSIGNMENT_FAILED");
                });
    }

    @Test
    void rejectsInsertFailureInsteadOfReturningAnUnpersistedGrant() {
        ApiKeyInterfaceService service = spy(new ApiKeyInterfaceService());
        doReturn(null).when(service).getOne(any(LambdaQueryWrapper.class));
        doReturn(false).when(service).save(any(ApiKeyInterface.class));

        assertThatThrownBy(() -> service.grant(
                7L, 11L, GrantSource.APPROVAL, 13L,
                LocalDateTime.now().plusDays(1), 17L, false, null))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo("API_KEY_INTERFACE_GRANT_FAILED");
                });
    }

    @Test
    void rejectsOptimisticUpdateFailureInsteadOfReturningAStaleGrant() {
        ApiKeyInterfaceService service = spy(new ApiKeyInterfaceService());
        ApiKeyInterface existing = new ApiKeyInterface();
        existing.setId(19L);
        doReturn(existing).when(service).getOne(any(LambdaQueryWrapper.class));
        doReturn(false).when(service).updateById(any(ApiKeyInterface.class));

        assertThatThrownBy(() -> service.grant(
                7L, 11L, GrantSource.APPROVAL, 13L,
                LocalDateTime.now().plusDays(1), 17L, false, null))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo("API_KEY_INTERFACE_GRANT_FAILED");
                });
    }
}
