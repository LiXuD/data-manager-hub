package com.dataplatform.governance.monitor.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.governance.monitor.service.ServiceHealthService;
import com.dataplatform.governance.monitor.vo.ServiceHealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alert/health")
public class HealthController {

    private final ServiceHealthService serviceHealthService;

    public HealthController(ServiceHealthService serviceHealthService) {
        this.serviceHealthService = serviceHealthService;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String status) {
        List<ServiceHealthVO> services = new ArrayList<>();
        for (String discoveredService : serviceHealthService.getServices()) {
            if (serviceName != null && !serviceName.isBlank()
                    && !discoveredService.toLowerCase().contains(serviceName.toLowerCase())) {
                continue;
            }
            ServiceHealthVO health = serviceHealthService.inspect(discoveredService);
            if (status == null || status.isBlank() || status.equals(health.status())) {
                services.add(health);
            }
        }

        long healthyCount = services.stream().filter(item -> "healthy".equals(item.status())).count();
        long avgResponseTime = Math.round(services.stream()
                .mapToLong(ServiceHealthVO::responseTime)
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
    public Result<ServiceHealthVO> check(@PathVariable String serviceName) {
        return Result.success(serviceHealthService.inspect(serviceName));
    }
}
