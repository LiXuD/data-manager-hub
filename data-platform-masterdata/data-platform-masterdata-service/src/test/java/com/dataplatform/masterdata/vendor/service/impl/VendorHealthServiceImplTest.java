package com.dataplatform.masterdata.vendor.service.impl;

import com.dataplatform.masterdata.connector.service.ActiveVendorConnectorRuntimeService;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorHealthServiceImplTest {

    @Mock
    private VendorConfigMapper vendorConfigMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ActiveVendorConnectorRuntimeService connectorRuntimeService;

    @Test
    void shouldExecutePublishedConnectorRuntimeForHealthCheck() {
        VendorConfig config = new VendorConfig();
        config.setId(10L);
        config.setVendorId(20L);

        when(vendorConfigMapper.selectById(10L)).thenReturn(config);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(connectorRuntimeService.execute(10L, Map.of()))
                .thenReturn(Map.of("success", true, "pipelineVersion", 3));

        VendorHealthServiceImpl service = new VendorHealthServiceImpl();
        ReflectionTestUtils.setField(service, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "connectorRuntimeService", connectorRuntimeService);

        Map<String, Object> result = service.testConnection(10L);

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        verify(connectorRuntimeService).execute(10L, Map.of());
    }
}
