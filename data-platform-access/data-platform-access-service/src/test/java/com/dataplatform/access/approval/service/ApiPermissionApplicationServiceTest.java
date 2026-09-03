package com.dataplatform.access.approval.service;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApplicationUpsertRequest;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.domain.ApiPermissionApplicationItem;
import com.dataplatform.access.approval.domain.ApiPermissionAction;
import com.dataplatform.access.approval.domain.ApplicationStatus;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationMapper;
import com.dataplatform.access.approval.mapper.ApprovalProcessConfigMapper;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiPermissionApplicationServiceTest {

    @Mock private ApiPermissionApplicationMapper applicationMapper;
    @Mock private ApiPermissionApplicationItemMapper itemMapper;
    @Mock private ApiPermissionActionMapper actionMapper;
    @Mock private ApprovalProcessConfigMapper processConfigMapper;
    @Mock private CallerService callerService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private ApiKeyInterfaceService grantService;
    @Mock private IdentityAccessInternalFeignClient identityClient;
    @Mock private ApiInterfaceFeignClient interfaceClient;
    @Mock private ApprovalEnginePort approvalEngine;
    @Mock private PlatformTransactionManager transactionManager;

    private ApiPermissionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApiPermissionApplicationService(
                applicationMapper,
                itemMapper,
                actionMapper,
                processConfigMapper,
                callerService,
                apiKeyService,
                grantService,
                identityClient,
                interfaceClient,
                approvalEngine,
                transactionManager);
    }

    @Test
    void shouldFailClosedWhenOptimisticApplicationUpdateLosesRace() {
        ApiPermissionApplication application = new ApiPermissionApplication();
        application.setId(1L);
        application.setVersion(3);
        when(applicationMapper.updateById(application)).thenReturn(0);

        assertThatThrownBy(() -> service.updateApplication(application))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getErrorCode()).isEqualTo("APPLICATION_VERSION_CONFLICT");
                });
    }

    @Test
    void shouldFailClosedWhenApplicationInsertFails() {
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(applicationMapper.insert(any(ApiPermissionApplication.class))).thenReturn(0);

        CallerInfo caller = new CallerInfo();
        caller.setId(1L);
        caller.setTenantId(7L);
        caller.setStatus(CommonStatus.ACTIVE);
        when(callerService.getById(1L)).thenReturn(caller);

        com.dataplatform.access.caller.entity.ApiKey apiKey =
                new com.dataplatform.access.caller.entity.ApiKey();
        apiKey.setId(2L);
        apiKey.setCallerId(1L);
        apiKey.setStatus(com.dataplatform.common.enums.ApiKeyStatus.ACTIVE);
        when(apiKeyService.getById(2L)).thenReturn(apiKey);

        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(3L);
        apiInterface.setInterfaceCode("DEMO");
        apiInterface.setInterfaceName("Demo");
        apiInterface.setStatus("active");
        when(interfaceClient.batchGet(List.of(3L)))
                .thenReturn(com.dataplatform.api.Result.success(List.of(apiInterface)));

        assertThatThrownBy(() -> service.createDraft(
                request(), 22L, "alice", 7L, true))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("APPLICATION_CREATE_FAILED");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
        verify(itemMapper, never()).insert(any(ApiPermissionApplicationItem.class));
    }

    @Test
    void shouldFailClosedWhenApplicationItemUpdateFails() {
        ApiPermissionApplicationItem item = new ApiPermissionApplicationItem();
        item.setId(9L);
        when(itemMapper.updateById(item)).thenReturn(0);

        assertThatThrownBy(() -> service.updateItem(item))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("APPLICATION_ITEM_VERSION_CONFLICT"));
    }

    @Test
    void shouldFailClosedWhenApplicationItemInsertFails() {
        ApiPermissionApplication application = new ApiPermissionApplication();
        application.setId(10L);
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(3L);
        apiInterface.setInterfaceCode("DEMO");
        apiInterface.setInterfaceName("Demo");
        apiInterface.setStatus("active");
        ApplicationUpsertRequest request = request();
        when(itemMapper.insert(any(ApiPermissionApplicationItem.class))).thenReturn(0);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "replaceItems", application, List.of(apiInterface),
                ApplicationStatus.DRAFT, request))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("APPLICATION_ITEM_CREATE_FAILED"));
    }

    @Test
    void shouldFailClosedWhenApplicationItemStatusUpdateFails() {
        ApiPermissionApplicationItem item = new ApiPermissionApplicationItem();
        item.setId(9L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(itemMapper.updateById(item)).thenReturn(0);

        assertThatThrownBy(() -> service.updateItemStatus(10L, ApplicationStatus.IN_REVIEW))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("APPLICATION_ITEM_VERSION_CONFLICT"));
    }

    @Test
    void shouldFailClosedWhenApplicationAuditInsertFails() {
        ApiPermissionApplication application = new ApiPermissionApplication();
        application.setId(10L);
        when(actionMapper.insert(any(ApiPermissionAction.class))).thenReturn(0);

        assertThatThrownBy(() -> service.appendAction(
                application, "CREATE", "USER", 22L, "alice",
                null, ApplicationStatus.DRAFT.name(), null, null))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("APPLICATION_AUDIT_WRITE_FAILED");
                    assertThat(exception.getStatus().value()).isEqualTo(503);
                });
    }

    @Test
    void shouldRequireRequestedCacheDaysWhenCacheIsEnabled() {
        ApplicationUpsertRequest request = new ApplicationUpsertRequest(
                "OPEN",
                1L,
                2L,
                List.of(3L),
                "用于风控系统贷前审批风险判断",
                "贷前审批",
                1000L,
                LocalDateTime.now().plusDays(30),
                null,
                true,
                null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateRequest", request, false))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_CACHE_POLICY");
                });
    }

    @Test
    void shouldRejectCacheDaysWhenCacheWasNotRequested() {
        ApplicationUpsertRequest request = new ApplicationUpsertRequest(
                "OPEN",
                1L,
                2L,
                List.of(3L),
                "用于后台服务批量查询业务数据",
                "后台批处理",
                1000L,
                LocalDateTime.now().plusDays(30),
                null,
                false,
                10);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateRequest", request, false))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("INVALID_CACHE_POLICY"));
    }

    @Test
    void shouldAllowRequestedExpiryMoreThan365DaysWhenSubmitting() {
        ApplicationUpsertRequest request = new ApplicationUpsertRequest(
                "OPEN",
                1L,
                2L,
                List.of(3L),
                "用于后台服务批量查询业务数据",
                "后台批处理",
                1000L,
                LocalDateTime.now().plusYears(10),
                null,
                false,
                null);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                service, "validateRequest", request, true))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectPastRequestedExpiryWhenSubmitting() {
        ApplicationUpsertRequest request = new ApplicationUpsertRequest(
                "OPEN",
                1L,
                2L,
                List.of(3L),
                "用于后台服务批量查询业务数据",
                "后台批处理",
                1000L,
                LocalDateTime.now().minusMinutes(1),
                null,
                false,
                null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validateRequest", request, true))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("INVALID_EXPIRY"));
    }

    @Test
    void shouldMapApprovalEngineCurrentTaskFailureWhenCancelling() {
        ApiPermissionApplication application = inReviewApplication();
        when(applicationMapper.selectById(100L)).thenReturn(application);
        when(approvalEngine.getCurrentTasks("process-1"))
                .thenThrow(new IllegalStateException("engine unavailable"));

        assertThatThrownBy(() -> service.cancel(100L, 22L, "alice", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(503);
                    assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_ENGINE_UNAVAILABLE");
                });
        verify(applicationMapper, never()).updateById(any(ApiPermissionApplication.class));
    }

    @Test
    void shouldMapApprovalEnginePolicyFailureWhenCancelling() {
        ApiPermissionApplication application = inReviewApplication();
        when(applicationMapper.selectById(100L)).thenReturn(application);
        when(approvalEngine.getCurrentTasks("process-1")).thenReturn(List.of(activeTask()));
        when(approvalEngine.getTaskPolicy("task-1"))
                .thenThrow(new IllegalStateException("policy unavailable"));

        assertThatThrownBy(() -> service.cancel(100L, 22L, "alice", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(503);
                    assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_ENGINE_UNAVAILABLE");
                });
        verify(approvalEngine, never()).terminate(any(), any());
        verify(applicationMapper, never()).updateById(any(ApiPermissionApplication.class));
    }

    @Test
    void shouldMapApprovalEngineTerminationFailureWhenCancelling() {
        ApiPermissionApplication application = inReviewApplication();
        when(applicationMapper.selectById(100L)).thenReturn(application);
        when(approvalEngine.getCurrentTasks("process-1")).thenReturn(List.of(activeTask()));
        when(approvalEngine.getTaskPolicy("task-1")).thenReturn(new ApprovalEnginePort.TaskPolicy(
                true, false, Set.of(), List.of()));
        org.mockito.Mockito.doThrow(new IllegalStateException("termination failed"))
                .when(approvalEngine).terminate("process-1", "申请人撤回");

        assertThatThrownBy(() -> service.cancel(100L, 22L, "alice", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(503);
                    assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_ENGINE_UNAVAILABLE");
                });
        verify(applicationMapper, never()).updateById(any(ApiPermissionApplication.class));
    }

    @Test
    void shouldExposeAllActiveTenantCallersToSystemAdmin() {
        CallerInfo caller = new CallerInfo();
        caller.setId(11L);
        caller.setCallerCode("INTERNAL_SYSTEM");
        caller.setCallerName("内部系统");
        caller.setTenantId(7L);
        caller.setStatus(CommonStatus.ACTIVE);
        when(callerService.listByTenant(7L)).thenReturn(List.of(caller));

        var result = service.eligibleCallers(22L, 7L, true);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(11L);
            assertThat(option.callerCode()).isEqualTo("INTERNAL_SYSTEM");
        });
        verify(callerService).listByTenant(7L);
        verify(identityClient, never()).getCallerIds(any());
    }

    @Test
    void shouldKeepRegularUserCallerScopeRestricted() {
        when(identityClient.getCallerIds(22L))
                .thenReturn(com.dataplatform.api.Result.success(List.of()));

        assertThat(service.eligibleCallers(22L, 7L, false)).isEmpty();
        verify(callerService, never()).listByTenant(any());
    }

    private ApplicationUpsertRequest request() {
        return new ApplicationUpsertRequest(
                "OPEN", 1L, 2L, List.of(3L),
                "用于后台服务批量查询业务数据", "后台批处理", 1000L,
                LocalDateTime.now().plusDays(30), null, false, null);
    }

    private ApiPermissionApplication inReviewApplication() {
        ApiPermissionApplication application = new ApiPermissionApplication();
        application.setId(100L);
        application.setApplicantUserId(22L);
        application.setTenantId(7L);
        application.setStatus(ApplicationStatus.IN_REVIEW.name());
        application.setProcessInstanceId("process-1");
        return application;
    }

    private ApprovalEnginePort.TaskSnapshot activeTask() {
        return new ApprovalEnginePort.TaskSnapshot(
                "task-1", "process-1", "definition-1", "approval", "接口权限审批", null,
                LocalDateTime.now());
    }
}
