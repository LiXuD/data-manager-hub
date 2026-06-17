package com.dataplatform.governance.log.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.governance.log.entity.OperationLog;
import com.dataplatform.governance.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 观测治理域操作日志的 Internal Log Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/log/internal")
public class InternalLogController {

    @Autowired
    private LogService logService;

    @PostMapping("/save")
    public Result<Void> saveLog(@RequestBody Map<String, Object> logData) {
        OperationLog log = new OperationLog();
        if (logData.get("userId") != null) {
            log.setUserId(Long.valueOf(logData.get("userId").toString()));
        }
        log.setUsername((String) logData.get("username"));
        log.setModule((String) logData.get("module"));
        log.setOperation((String) logData.get("operation"));
        log.setMethod((String) logData.get("method"));
        log.setParams((String) logData.get("params"));
        log.setResult((String) logData.get("result"));
        log.setIp((String) logData.get("ip"));
        log.setLocation((String) logData.get("location"));
        if (logData.get("duration") != null) {
            log.setDuration(Integer.valueOf(logData.get("duration").toString()));
        }
        log.setStatus((String) logData.get("status"));
        log.setCreatedAt(LocalDateTime.now());

        logService.save(log);
        return Result.success(null);
    }
}
