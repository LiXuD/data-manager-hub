package com.dataplatform.access.caller.service;

import com.dataplatform.access.caller.entity.ApiKeyProduct;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class ApiKeyProductServiceTest {

    @Test
    void rejectsBatchWriteFailureAfterReplacingProductGrants() {
        ApiKeyProductService service = spy(new ApiKeyProductService());
        doReturn(true).when(service).remove(any());
        doReturn(false).when(service).saveBatch(anyList());

        assertThrows(ApiKeyProvisioningException.class,
                () -> service.assignProducts(7L, List.of(11L)));
    }
}
