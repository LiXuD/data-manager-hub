package com.dataplatform.governance.monitor.service;

import com.dataplatform.governance.monitor.entity.ServiceHealthCheck;
import com.dataplatform.governance.monitor.mapper.ServiceHealthCheckMapper;
import com.dataplatform.governance.monitor.vo.ServiceHealthVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceHealthServiceTest {

    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private ServiceHealthCheckMapper checkMapper;

    @Test
    void shouldUseRegistryAvailabilityWithoutCallingServiceEndpoints() {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(discoveryClient.getInstances("data-platform-access")).thenReturn(List.of(instance));
        when(checkMapper.selectList(any())).thenReturn(List.of());
        ServiceHealthService service = new ServiceHealthService(discoveryClient, checkMapper);

        ServiceHealthVO health = service.inspect("data-platform-access");

        assertEquals("healthy", health.status());
        assertEquals(1, health.instanceCount());
        ArgumentCaptor<ServiceHealthCheck> check = ArgumentCaptor.forClass(ServiceHealthCheck.class);
        verify(checkMapper).insert(check.capture());
        assertEquals(Boolean.TRUE, check.getValue().getHealthy());
    }
}
