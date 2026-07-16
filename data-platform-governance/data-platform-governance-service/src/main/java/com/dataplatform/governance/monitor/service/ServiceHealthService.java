package com.dataplatform.governance.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.governance.monitor.entity.ServiceHealthCheck;
import com.dataplatform.governance.monitor.mapper.ServiceHealthCheckMapper;
import com.dataplatform.governance.monitor.vo.ServiceHealthVO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

@Service
public class ServiceHealthService {
    private final DiscoveryClient discoveryClient;
    private final ServiceHealthCheckMapper checkMapper;

    public ServiceHealthService(DiscoveryClient discoveryClient,
                                ServiceHealthCheckMapper checkMapper) {
        this.discoveryClient = discoveryClient;
        this.checkMapper = checkMapper;
    }

    public List<String> getServices() {
        return discoveryClient.getServices();
    }

    public ServiceHealthVO inspect(String serviceName) {
        long start = System.nanoTime();
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        long responseTime = elapsedMillis(start);
        boolean healthy = !instances.isEmpty();
        LocalDateTime checkedAt = LocalDateTime.now();
        saveCheck(serviceName, healthy, responseTime, instances.size(), checkedAt);
        return new ServiceHealthVO(serviceName, healthy ? "healthy" : "unhealthy", responseTime,
                calculateUptime(serviceName), instances.size(), checkedAt);
    }

    private void saveCheck(String serviceName, boolean healthy, long responseTime,
                           int instanceCount, LocalDateTime checkedAt) {
        ServiceHealthCheck check = new ServiceHealthCheck();
        check.setServiceName(serviceName);
        check.setHealthy(healthy);
        check.setResponseTime(responseTime);
        check.setInstanceCount(instanceCount);
        check.setCheckedAt(checkedAt);
        checkMapper.insert(check);
    }

    private double calculateUptime(String serviceName) {
        List<ServiceHealthCheck> checks = checkMapper.selectList(
                new LambdaQueryWrapper<ServiceHealthCheck>()
                        .eq(ServiceHealthCheck::getServiceName, serviceName)
                        .ge(ServiceHealthCheck::getCheckedAt, LocalDateTime.now().minusHours(24)));
        if (checks.isEmpty()) {
            return 0D;
        }
        long healthyChecks = checks.stream().filter(item -> Boolean.TRUE.equals(item.getHealthy())).count();
        return Math.round((healthyChecks * 10000D / checks.size())) / 100D;
    }

    private long elapsedMillis(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }
}
