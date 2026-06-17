package com.dataplatform.governance.log.api;

import com.dataplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 观测治理域操作日志的 Log Client。
 * <p>日志治理组件，负责记录、转发或查询操作日志。</p>
 */
@FeignClient(name = "data-platform-governance", contextId = "governanceLogClient")
public interface LogClient {

    @PostMapping("/log/internal/save")
    Result<Void> saveLog(@RequestBody Map<String, Object> logData);
}
