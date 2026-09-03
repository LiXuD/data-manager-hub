package com.dataplatform.access.approval.service;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.CompleteTaskRequest;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;
import com.dataplatform.api.Result;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiPermissionTaskServiceTest {

    @Mock
    private ApprovalEnginePort approvalEngine;
    @Mock
    private ApiPermissionApplicationService applicationService;
    @Mock
    private IdentityAccessInternalFeignClient identityClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ApiPermissionTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new ApiPermissionTaskService(
                approvalEngine,
                applicationService,
                identityClient,
                transactionManager);
    }

    @Test
    void shouldClaimTaskOnlyWhenUserBelongsToCandidateGroup() {
        ApiPermissionApplication application = application(100L, 7L, 11L, 3);
        ApprovalEnginePort.TaskSnapshot candidate = task(null);
        ApprovalEnginePort.TaskSnapshot claimed = task("22");
        when(approvalEngine.getTask("task-1"))
                .thenReturn(Optional.of(candidate), Optional.of(claimed));
        when(applicationService.findByProcessInstance("process-1")).thenReturn(application);
        when(identityClient.getRoleCodes(22L))
                .thenReturn(Result.success(List.of("API_INTERFACE_APPROVER")));
        when(approvalEngine.canClaim(
                "task-1", Set.of("api_interface_approver"))).thenReturn(true);

        var result = taskService.claim("task-1", 22L, 7L);

        assertThat(result.task().assignee()).isEqualTo("22");
        verify(approvalEngine).claim("task-1", "22");
        verify(applicationService).updateApplication(application);
    }

    @Test
    void shouldRejectSelfApprovalBeforeClaim() {
        ApiPermissionApplication application = application(100L, 7L, 22L, 3);
        ApprovalEnginePort.TaskSnapshot candidate = task(null);
        when(approvalEngine.getTask("task-1")).thenReturn(Optional.of(candidate));
        when(applicationService.findByProcessInstance("process-1")).thenReturn(application);

        assertThatThrownBy(() -> taskService.claim("task-1", 22L, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("SELF_APPROVAL_FORBIDDEN"));
        verify(approvalEngine, never()).claim(any(), any());
    }

    @Test
    void shouldRejectCrossTenantTask() {
        ApiPermissionApplication application = application(100L, 8L, 11L, 3);
        when(approvalEngine.getTask("task-1")).thenReturn(Optional.of(task(null)));
        when(applicationService.findByProcessInstance("process-1")).thenReturn(application);

        assertThatThrownBy(() -> taskService.claim("task-1", 22L, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("TASK_TENANT_DENIED"));
    }

    @Test
    void shouldRequireUserAndTenantScopeBeforeListingTasks() {
        assertThatThrownBy(() -> taskService.listTasks(22L, null))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("TENANT_SCOPE_REQUIRED"));

        assertThatThrownBy(() -> taskService.listTasks(null, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("USER_SCOPE_REQUIRED"));
    }

    @Test
    void shouldFailClosedWhenApprovalEngineReturnsNullTaskCollection() {
        when(identityClient.getRoleCodes(22L)).thenReturn(Result.success(List.of()));
        when(approvalEngine.findTasks("22", Set.of())).thenReturn(null);

        assertThatThrownBy(() -> taskService.listTasks(22L, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("APPROVAL_ENGINE_UNAVAILABLE"));
    }

    @Test
    void shouldFailClosedWhenApprovalEngineReturnsNullTaskLookup() {
        when(approvalEngine.getTask("task-1")).thenReturn(null);

        assertThatThrownBy(() -> taskService.claim("task-1", 22L, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("APPROVAL_ENGINE_UNAVAILABLE"));
    }

    @Test
    void shouldRejectInvalidTaskPolicyInsteadOfReturningRawServerError() {
        ApiPermissionApplication application = application(100L, 7L, 11L, 3);
        when(approvalEngine.getTask("task-1")).thenReturn(Optional.of(task(null)));
        when(applicationService.findByProcessInstance("process-1")).thenReturn(application);
        when(identityClient.getRoleCodes(22L)).thenReturn(Result.success(List.of()));
        when(approvalEngine.canClaim("task-1", Set.of())).thenReturn(true);
        when(approvalEngine.getTaskPolicy("task-1")).thenReturn(new ApprovalEnginePort.TaskPolicy(
                true,
                true,
                Set.of("APPROVE"),
                List.of(new ApprovalEnginePort.FormField(
                        null, "无效字段", "string", false, null, List.of()))));

        assertThatThrownBy(() -> taskService.taskDetail("task-1", 22L, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("APPROVAL_CONFIG_INVALID"));
    }

    @Test
    void shouldFailClosedWhenProcessDiagnosticsAreUnavailable() {
        when(approvalEngine.processDiagnostics()).thenReturn(null);

        assertThatThrownBy(() -> taskService.processDiagnostics())
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("APPROVAL_ENGINE_UNAVAILABLE"));
    }

    @Test
    void shouldFailClosedWhenProcessHistoryIsUnavailable() {
        ApiPermissionApplication application = application(100L, 7L, 22L, 3);
        when(applicationService.requireVisibleApplication(100L, 22L, 7L, false))
                .thenReturn(application);
        when(approvalEngine.history("process-1")).thenReturn(null);

        assertThatThrownBy(() -> taskService.processHistory(100L, 22L, 7L, false))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("APPROVAL_ENGINE_UNAVAILABLE"));
    }

    @Test
    void shouldRejectStaleApplicationVersionBeforeCompletingTask() {
        ApiPermissionApplication application = application(100L, 7L, 11L, 4);
        application.setRequestedExpireAt(LocalDateTime.now().plusDays(10));
        when(approvalEngine.getTask("task-1")).thenReturn(Optional.of(task("22")));
        when(approvalEngine.getTaskPolicy("task-1")).thenReturn(defaultPolicy());
        when(applicationService.findByProcessInstance("process-1")).thenReturn(application);

        CompleteTaskRequest request = new CompleteTaskRequest(
                3,
                "APPROVE",
                LocalDateTime.now().plusDays(5),
                "同意",
                Map.of(),
                false,
                null);

        assertThatThrownBy(() -> taskService.complete(
                "task-1", request, 22L, "approver", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("APPLICATION_VERSION_CONFLICT"));
        verify(approvalEngine, never()).complete(any(), any(), any());
    }

    @Test
    void shouldRejectStructuredApprovalFormValues() {
        when(approvalEngine.getTask("task-1")).thenReturn(Optional.of(task("22")));
        when(approvalEngine.getTaskPolicy("task-1")).thenReturn(new ApprovalEnginePort.TaskPolicy(
                true,
                true,
                Set.of("APPROVE", "REJECT"),
                List.of(new ApprovalEnginePort.FormField(
                        "unsafe", "扩展字段", "string", false, null, List.of()))));
        CompleteTaskRequest request = new CompleteTaskRequest(
                3,
                "APPROVE",
                LocalDateTime.now().plusDays(5),
                "同意",
                Map.of("unsafe", List.of("nested")),
                false,
                null);

        assertThatThrownBy(() -> taskService.complete(
                "task-1", request, 22L, "approver", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("INVALID_FORM_FIELD_VALUE"));
        verify(approvalEngine, never()).complete(any(), any(), any());
    }

    @Test
    void shouldRejectApprovedCacheDaysAboveRequestedLimit() {
        ApiPermissionApplication application = application(100L, 7L, 11L, 4);
        ApiPermissionApplicationItem item = new ApiPermissionApplicationItem();
        item.setRequestedCacheEnabled(true);
        item.setRequestedCacheDays(2);
        when(applicationService.listItems(100L)).thenReturn(List.of(item));
        CompleteTaskRequest request = new CompleteTaskRequest(
                4,
                "APPROVE",
                LocalDateTime.now().plusDays(5),
                "同意接口权限",
                Map.of(),
                true,
                10);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                taskService, "resolveApprovedCachePolicy", application, request))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("INVALID_CACHE_APPROVAL"));
    }

    @Test
    void shouldRequireExplicitCacheDecisionWhenCacheWasRequested() {
        ApiPermissionApplication application = application(100L, 7L, 11L, 4);
        ApiPermissionApplicationItem item = new ApiPermissionApplicationItem();
        item.setRequestedCacheEnabled(true);
        item.setRequestedCacheDays(10);
        when(applicationService.listItems(100L)).thenReturn(List.of(item));
        CompleteTaskRequest request = new CompleteTaskRequest(
                4,
                "APPROVE",
                LocalDateTime.now().plusDays(5),
                "同意接口权限",
                Map.of(),
                null,
                null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                taskService, "resolveApprovedCachePolicy", application, request))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("CACHE_DECISION_REQUIRED"));
    }

    private ApiPermissionApplication application(
            Long id,
            Long tenantId,
            Long applicantUserId,
            int version) {
        ApiPermissionApplication application = new ApiPermissionApplication();
        application.setId(id);
        application.setTenantId(tenantId);
        application.setApplicantUserId(applicantUserId);
        application.setStatus(ApplicationStatus.IN_REVIEW.name());
        application.setProcessInstanceId("process-1");
        application.setVersion(version);
        return application;
    }

    private ApprovalEnginePort.TaskSnapshot task(String assignee) {
        return new ApprovalEnginePort.TaskSnapshot(
                "task-1",
                "process-1",
                "definition-1",
                "apiPermissionApprovalTask",
                "接口权限审批",
                assignee,
                LocalDateTime.now());
    }

    private ApprovalEnginePort.TaskPolicy defaultPolicy() {
        return new ApprovalEnginePort.TaskPolicy(
                true,
                true,
                Set.of("APPROVE", "REJECT"),
                List.of());
    }
}
