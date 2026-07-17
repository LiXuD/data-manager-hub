package com.dataplatform.access.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.service.ApiKeyInterfaceService;
import com.dataplatform.access.caller.service.ApiKeyService;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.ApiKeyStatus;
import com.dataplatform.common.util.UserContext;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiDocumentControllerAuthorizationTest {

    private final ApiInterfaceFeignClient interfaceClient = mock(ApiInterfaceFeignClient.class);
    private final OpenApiDocumentService documentService = new OpenApiDocumentService(new ObjectMapper());
    private final OpenApiBaseUrlResolver baseUrlResolver = new OpenApiBaseUrlResolver("https://platform.example.com");

    @Test
    void managementDocumentRejectsUserWithoutInterfaceViewPermission() {
        OpenApiDocumentController controller = new OpenApiDocumentController(
                interfaceClient, documentService, baseUrlResolver);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("interface:view")).thenReturn(false);

            assertThat(controller.detail(1L).getCode()).isEqualTo(403);
            assertThat(controller.download(1L, "json").getStatusCode().value()).isEqualTo(403);
            verifyNoInteractions(interfaceClient);
        }
    }

    @Test
    void callerDocumentListsOnlyAuthorizedActiveInterfacesWithoutConsumingQuota() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiKeyInterfaceService permissionService = mock(ApiKeyInterfaceService.class);
        CallerOpenApiDocumentController controller = new CallerOpenApiDocumentController(
                apiKeyService, permissionService, interfaceClient, documentService, baseUrlResolver);
        ApiKey key = new ApiKey();
        key.setId(9L);
        key.setStatus(ApiKeyStatus.ACTIVE);
        when(apiKeyService.getByKey("caller-key")).thenReturn(key);
        when(permissionService.getInterfaceIdsByApiKeyId(9L)).thenReturn(List.of(1L, 2L));
        ApiInterfaceDTO active = interfaceInfo(1L, "WORLD_TIME", "active");
        ApiInterfaceDTO disabled = interfaceInfo(2L, "DISABLED", "inactive");
        when(interfaceClient.getById(1L)).thenReturn(Result.success(active));
        when(interfaceClient.getById(2L)).thenReturn(Result.success(disabled));

        var result = controller.list("caller-key", null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).get("interfaceCode")).isEqualTo("WORLD_TIME");
        verify(apiKeyService, never()).validateAndConsumeQuota(anyString(), anyLong());
    }

    @Test
    void callerDocumentDoesNotExposeUnassignedInterfaceContract() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiKeyInterfaceService permissionService = mock(ApiKeyInterfaceService.class);
        CallerOpenApiDocumentController controller = new CallerOpenApiDocumentController(
                apiKeyService, permissionService, interfaceClient, documentService, baseUrlResolver);
        ApiKey key = new ApiKey();
        key.setId(9L);
        key.setStatus(ApiKeyStatus.ACTIVE);
        when(apiKeyService.getByKey("caller-key")).thenReturn(key);
        when(interfaceClient.getByInterfaceCode("WORLD_TIME"))
                .thenReturn(Result.success(interfaceInfo(1L, "WORLD_TIME", "active")));
        when(permissionService.hasInterfacePermission(9L, 1L)).thenReturn(false);

        var result = controller.detail("WORLD_TIME", "caller-key", null);

        assertThat(result.getCode()).isEqualTo(403);
        verify(interfaceClient, never()).getContract(1L);
    }

    private ApiInterfaceDTO interfaceInfo(Long id, String code, String status) {
        ApiInterfaceDTO dto = new ApiInterfaceDTO();
        dto.setId(id);
        dto.setInterfaceCode(code);
        dto.setInterfaceName(code);
        dto.setStatus(status);
        return dto;
    }
}
