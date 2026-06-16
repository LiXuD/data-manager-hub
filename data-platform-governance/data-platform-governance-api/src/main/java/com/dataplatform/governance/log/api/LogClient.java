package com.dataplatform.governance.log.api;

import com.dataplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "data-platform-governance", contextId = "governanceLogClient")
public interface LogClient {

    @PostMapping("/log/internal/save")
    Result<Void> saveLog(@RequestBody Map<String, Object> logData);
}
