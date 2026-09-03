package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKey;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyProvisioningServiceTest {

    @Test
    void createsKeyBeforePersistingItsProductGrants() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiKeyProductService apiKeyProductService = mock(ApiKeyProductService.class);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(9L);
        when(apiKeyService.createApiKey(1L, "production")).thenReturn(apiKey);
        ApiKeyProvisioningService service = new ApiKeyProvisioningService(apiKeyService, apiKeyProductService);

        ApiKey result = service.create(1L, "production", List.of(10L, 11L));

        assertSame(apiKey, result);
        var order = inOrder(apiKeyService, apiKeyProductService);
        order.verify(apiKeyService).createApiKey(1L, "production");
        order.verify(apiKeyProductService).assignProducts(9L, List.of(10L, 11L));
    }

    @Test
    void rejectsAnUnavailableCreatedKeyBeforeWritingGrants() {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiKeyProductService apiKeyProductService = mock(ApiKeyProductService.class);
        when(apiKeyService.createApiKey(1L, "production")).thenReturn(null);
        ApiKeyProvisioningService service = new ApiKeyProvisioningService(apiKeyService, apiKeyProductService);

        assertThrows(ApiKeyProvisioningException.class,
                () -> service.create(1L, "production", List.of(10L)));
    }
}
