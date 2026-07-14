package com.dataplatform.governance.monitor.controller;

import com.dataplatform.common.result.Result;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert/health")
public class HealthController {

    private final DiscoveryClient discoveryClient;

    public HealthController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String status) {
        List<ServiceHealth> services = new ArrayList<>();
        for (String discoveredService : discoveryClient.getServices()) {
            if (serviceName != null && !serviceName.isBlank()
                    && !discoveredService.toLowerCase().contains(serviceName.toLowerCase())) {
                continue;
            }
            ServiceHealth health = inspect(discoveredService);
            if (status == null || status.isBlank() || status.equals(health.status())) {
                services.add(health);
            }
        }

        long healthyCount = services.stream().filter(item -> "healthy".equals(item.status())).count();
        long avgResponseTime = Math.round(services.stream()
                .mapToLong(ServiceHealth::responseTime)
                .average()
                .orElse(0D));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalServices", services.size());
        stats.put("healthyCount", healthyCount);
        stats.put("unhealthyCount", services.size() - healthyCount);
        stats.put("avgResponseTime", avgResponseTime);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", services);
        data.put("stats", stats);
        return Result.success(data);
    }

    @PostMapping("/{serviceName}/check")
    public Result<ServiceHealth> check(@PathVariable String serviceName) {
        return Result.success(inspect(serviceName));
    }

    private ServiceHealth inspect(String serviceName) {
        long start = System.nanoTime();
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        long responseTime = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        boolean healthy = !instances.isEmpty();
        return new ServiceHealth(
                serviceName,
                healthy ? "healthy" : "unhealthy",
                responseTime,
                healthy ? 100D : 0D,
                instances.size(),
                LocalDateTime.now());
    }

    public record ServiceHealth(
            String serviceName,
            String status,
            long responseTime,
            double uptime,
            int instanceCount,
            LocalDateTime lastCheck) {
    }
}
