package com.dataplatform.access.approval.service;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.domain.ApiPermissionApplication;
import com.dataplatform.access.approval.engine.ApprovalEnginePort;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationMapper;
import com.dataplatform.access.approval.mapper.ApprovalProcessConfigMapper;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.identity.api.feign.IdentityAccessInternalFeignClient;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
