package com.dataplatform.masterdata.vendor.service.impl;

import com.dataplatform.common.adapter.VendorAdapter;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorAdapterConfigAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorHealthServiceImplTest {

    @Mock
    private VendorConfigMapper vendorConfigMapper;
    @Mock
    private VendorInfoMapper vendorInfoMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private VendorAdapter vendorAdapter;

    @AfterEach
    void clearAdapters() {
        VendorAdapterFactory.clearCache();
    }

    @Test
    void shouldReuseAuthenticatedVendorAdapterConfiguration() {
        VendorConfig config = new VendorConfig();
        config.setId(10L);
        config.setVendorId(20L);
        config.setApiUrl("https://vendor.example/query");
        config.setMethod("POST");
        config.setHeaderConfig("{\"X-Tenant\":\"bank\"}");
        config.setAuthType("BEARER");
        config.setAuthConfig("{\"token\":\"vendor-token\"}");

        VendorInfo vendor = new VendorInfo();
        vendor.setId(20L);
        vendor.setVendorCode("VENDOR_A");
        vendor.setSecretKey("secret");

        when(vendorConfigMapper.selectById(10L)).thenReturn(config);
        when(vendorInfoMapper.selectById(20L)).thenReturn(vendor);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(vendorAdapter.execute(any(VendorAdapterConfig.class), anyMap()))
                .thenReturn(Map.of("success", true, "latency", 5L));
        VendorAdapterFactory.registerAdapter("VENDOR_A", vendorAdapter);

        VendorHealthServiceImpl service = new VendorHealthServiceImpl();
        ReflectionTestUtils.setField(service, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(service, "vendorInfoMapper", vendorInfoMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "adapterConfigAssembler",
                new VendorAdapterConfigAssembler(new ObjectMapper()));

        Map<String, Object> result = service.testConnection(10L);

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        ArgumentCaptor<VendorAdapterConfig> captured = ArgumentCaptor.forClass(VendorAdapterConfig.class);
        verify(vendorAdapter).execute(captured.capture(), anyMap());
        assertEquals("BEARER", captured.getValue().getAuthType());
        assertEquals("vendor-token", captured.getValue().getAuthConfig().get("token"));
        assertEquals("bank", captured.getValue().getHeaders().get("X-Tenant"));
        assertEquals("secret", captured.getValue().getSecretKey());
    }
}
