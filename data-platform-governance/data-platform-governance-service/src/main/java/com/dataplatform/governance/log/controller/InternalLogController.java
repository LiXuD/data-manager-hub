package com.dataplatform.governance.log.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.common.security.InternalScope;
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
@RequestMapping("/internal/v1/governance/logs")
    @InternalScope("governance:log")
public class InternalLogController {

    @Autowired
    private LogService logService;

    @PostMapping
    public Result<Void> saveLog(@RequestBody(required = false) Map<String, Object> logData) {
        if (logData == null) {
            return Result.error(400, "请求体不能为空");
        }
        OperationLog log = new OperationLog();
        try {
            log.setUserId(parseLong(logData.get("userId"), "userId"));
            log.setTenantId(parseLong(logData.get("tenantId"), "tenantId"));
            log.setUsername(asText(logData.get("username"), "username"));
            log.setModule(asText(logData.get("module"), "module"));
            log.setOperation(asText(logData.get("operation"), "operation"));
            log.setMethod(asText(logData.get("method"), "method"));
            log.setParams(asText(logData.get("params"), "params"));
            log.setResult(asText(logData.get("result"), "result"));
            log.setIp(asText(logData.get("ip"), "ip"));
            log.setLocation(asText(logData.get("location"), "location"));
            Integer duration = parseInteger(logData.get("duration"), "duration");
            if (duration != null && duration < 0) {
                return Result.error(400, "duration不能为负数");
            }
            log.setDuration(duration);
            log.setStatus(asText(logData.get("status"), "status"));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
        log.setCreatedAt(LocalDateTime.now());

        logService.saveLog(log);
        return Result.success(null);
    }

    private Long parseLong(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(field + "必须是有效数字");
            }
        }
        throw new IllegalArgumentException(field + "必须是数字");
    }

    private Integer parseInteger(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            if (number.longValue() != number.doubleValue()) {
                throw new IllegalArgumentException(field + "必须是整数");
            }
            try {
                return Math.toIntExact(number.longValue());
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(field + "超出整数范围");
            }
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(field + "必须是有效整数");
            }
        }
        throw new IllegalArgumentException(field + "必须是整数");
    }

    private String asText(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new IllegalArgumentException(field + "必须是文本");
    }
}
