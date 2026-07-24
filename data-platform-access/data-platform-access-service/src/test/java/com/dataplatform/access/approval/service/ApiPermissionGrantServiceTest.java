package com.dataplatform.access.approval.service;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.access.approval.api.EmergencyGrantRequest;
import com.dataplatform.access.approval.mapper.ApiPermissionActionMapper;
import com.dataplatform.access.approval.mapper.ApiPermissionApplicationItemMapper;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.access.caller.service.CallerService;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiPermissionGrantServiceTest {

    @Mock
    private ApiKeyInterfaceService grantService;
    @Mock
    private CallerService callerService;
    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private ApiInterfaceFeignClient interfaceClient;
    @Mock
    private ApiPermissionApplicationService applicationService;
    @Mock
    private ApiPermissionApplicationItemMapper itemMapper;
    @Mock
    private ApiPermissionActionMapper actionMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ApiPermissionGrantService service;

    @BeforeEach
    void setUp() {
        service = new ApiPermissionGrantService(
                grantService,
                callerService,
                apiKeyService,
                interfaceClient,
                applicationService,
                itemMapper,
                actionMapper,
                transactionManager);
    }

    @Test
    void shouldLimitEmergencyGrantToTwentyFourHours() {
        EmergencyGrantRequest request = new EmergencyGrantRequest(
                1L,
                2L,
                List.of(3L),
                LocalDateTime.now().plusHours(25),
                "生产故障应急恢复需要临时调用接口",
                "INC-2026-001");

        assertThatThrownBy(() -> service.emergencyGrant(
                request, 9L, "security-admin", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("INVALID_EMERGENCY_EXPIRY"));
    }

    @Test
    void shouldRequireEmergencyGrantTicketNumber() {
        EmergencyGrantRequest request = new EmergencyGrantRequest(
                1L,
                2L,
                List.of(3L),
                LocalDateTime.now().plusHours(1),
                "生产故障应急恢复需要临时调用接口",
                " ");

        assertThatThrownBy(() -> service.emergencyGrant(
                request, 9L, "security-admin", 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("INVALID_EMERGENCY_TICKET"));
    }

    @Test
    void shouldExposeOnlyActiveEmergencyInterfacesForTenantApiKey() {
        CallerInfo caller = caller(1L, 7L);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(2L);
        apiKey.setCallerId(1L);
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        ApiInterfaceDTO active = apiInterface(3L, "ACTIVE_API", "active");
        ApiInterfaceDTO disabled = apiInterface(4L, "DISABLED_API", "disabled");

        when(apiKeyService.getById(2L)).thenReturn(apiKey);
        when(callerService.getById(1L)).thenReturn(caller);
        when(interfaceClient.getOptions(null)).thenReturn(Result.success(List.of(active, disabled)));
        when(grantService.getInterfaceIdsByApiKeyId(2L)).thenReturn(List.of(3L));

        var options = service.emergencyInterfaces(2L, null, 7L);

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(3L);
            assertThat(option.granted()).isTrue();
            assertThat(option.pending()).isFalse();
        });
    }

    @Test
    void shouldRejectEmergencyOptionsForCallerFromAnotherTenant() {
        when(callerService.getById(1L)).thenReturn(caller(1L, 8L));

        assertThatThrownBy(() -> service.emergencyApiKeys(1L, 7L))
                .isInstanceOfSatisfying(ApiPermissionException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo("CALLER_TENANT_DENIED"));
    }

    private CallerInfo caller(Long id, Long tenantId) {
        CallerInfo caller = new CallerInfo();
        caller.setId(id);
        caller.setTenantId(tenantId);
        caller.setStatus(CommonStatus.ACTIVE);
        return caller;
    }

    private ApiInterfaceDTO apiInterface(Long id, String code, String status) {
        ApiInterfaceDTO apiInterface = new ApiInterfaceDTO();
        apiInterface.setId(id);
        apiInterface.setInterfaceCode(code);
        apiInterface.setInterfaceName(code);
        apiInterface.setStatus(status);
        return apiInterface;
    }
}
