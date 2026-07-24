package com.dataplatform.access.approval.workflow;

import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.domain.GrantSource;
import com.dataplatform.access.approval.service.ApiPermissionApplicationService;
import com.dataplatform.access.caller.entity.ApiKeyInterface;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrantApiPermissionDelegateTest {

    @Test
    void shouldPersistApprovedCachePolicyIntoGrant() {
        ApiPermissionApplicationService applicationService =
                mock(ApiPermissionApplicationService.class);
        ApiKeyInterfaceService grantService = mock(ApiKeyInterfaceService.class);
        DelegateExecution execution = mock(DelegateExecution.class);
        ApiPermissionApplication application = new ApiPermissionApplication();
        application.setId(100L);
        application.setApiKeyId(20L);
        application.setStatus(ApplicationStatus.IN_REVIEW.name());
        application.setApprovedExpireAt(LocalDateTime.now().plusDays(30));
        ApiPermissionApplicationItem item = new ApiPermissionApplicationItem();
        item.setId(200L);
        item.setInterfaceId(30L);
        item.setApprovedCacheEnabled(true);
        item.setApprovedCacheDays(2);
        ApiKeyInterface grant = new ApiKeyInterface();
        grant.setId(300L);

        when(execution.getVariable("applicationId")).thenReturn(100L);
        when(execution.getVariable("approverUserId")).thenReturn(9L);
        when(applicationService.requireApplication(100L)).thenReturn(application);
        when(applicationService.listItems(100L)).thenReturn(List.of(item));
        when(grantService.grant(
                20L,
                30L,
                GrantSource.APPROVAL,
                200L,
                application.getApprovedExpireAt(),
                9L,
                true,
                2)).thenReturn(grant);

        new GrantApiPermissionDelegate(applicationService, grantService).execute(execution);

        verify(grantService).grant(
                20L,
                30L,
                GrantSource.APPROVAL,
                200L,
                application.getApprovedExpireAt(),
                9L,
                true,
                2);
        verify(applicationService).updateItem(item);
    }
}
