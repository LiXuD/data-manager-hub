package com.dataplatform.access.approval.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.ApplicationUpsertRequest;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    void shouldExposeAllActiveTenantCallersToSystemAdmin() {
        CallerInfo caller = new CallerInfo();
        caller.setId(11L);
        caller.setCallerCode("INTERNAL_SYSTEM");
        caller.setCallerName("内部系统");
        caller.setTenantId(7L);
        caller.setStatus(CommonStatus.ACTIVE);
        when(callerService.list(any(Wrapper.class))).thenReturn(List.of(caller));

        var result = service.eligibleCallers(22L, 7L, true);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(11L);
            assertThat(option.callerCode()).isEqualTo("INTERNAL_SYSTEM");
        });
        verify(identityClient, never()).getCallerIds(any());
    }

    @Test
    void shouldKeepRegularUserCallerScopeRestricted() {
        when(identityClient.getCallerIds(22L))
                .thenReturn(com.dataplatform.api.Result.success(List.of()));

        assertThat(service.eligibleCallers(22L, 7L, false)).isEmpty();
        verify(callerService, never()).list(any(Wrapper.class));
    }
}
