package com.dataplatform.access.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.access.approval.domain.ApiPermissionAction;
import com.dataplatform.access.approval.domain.GrantSource;
import com.dataplatform.access.approval.domain.GrantStatus;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiPermissionGrantExpiryJobTest {

    @Mock private ApiKeyInterfaceService grantService;
    @Mock private ApiPermissionApplicationItemMapper itemMapper;
    @Mock private ApiPermissionActionMapper actionMapper;
    @Mock private ApiPermissionApplicationService applicationService;

    @Test
    void shouldAuditExpiryForEmergencyGrantWithoutApplication() {
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setId(9L);
        grant.setGrantSource(GrantSource.EMERGENCY_ADMIN.name());
        grant.setStatus(GrantStatus.ACTIVE.name());
        grant.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(grantService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(grant));
        when(grantService.updateById(grant)).thenReturn(true);

        new ApiPermissionGrantExpiryJob(
                grantService, itemMapper, actionMapper, applicationService).expireDueGrants();

        ArgumentCaptor<ApiPermissionAction> captor = ArgumentCaptor.forClass(ApiPermissionAction.class);
        verify(actionMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("EXPIRE");
        assertThat(captor.getValue().getActorType()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().getComment()).contains("grantId=9", "EMERGENCY_ADMIN");
    }
}
