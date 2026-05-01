package com.dataplatform.iam.aspect;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.log.OperationLogRecord;
import com.dataplatform.common.log.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    @Autowired
    private OperationLogService operationLogService;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        OperationLogRecord record = new OperationLogRecord();
        record.setCreatedAt(LocalDateTime.now());

        log.debug("OperationLog aspect triggered: module={}, operation={}", operationLog.module(), operationLog.operation());

        try {
            var signature = (org.aspectj.lang.reflect.MethodSignature) point.getSignature();
            record.setModule(operationLog.module());
            record.setOperation(operationLog.operation());
            record.setDescription(operationLog.description());
            record.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                record.setIp(getClientIp(request));
            }

            if (operationLog.saveParams() && objectMapper != null) {
                try {
                    Object[] args = point.getArgs();
                    if (args != null && args.length > 0) {
                        record.setParams(objectMapper.writeValueAsString(args));
                    }
                } catch (Exception ignored) {
                }
            }

            Object result = point.proceed();

            record.setStatus("success");
            if (operationLog.saveResult() && objectMapper != null && result != null) {
                try {
                    record.setResult(objectMapper.writeValueAsString(result));
                } catch (Exception ignored) {
                }
            }

            return result;
        } catch (Throwable e) {
            record.setStatus("fail");
            record.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            record.setDuration(System.currentTimeMillis() - startTime);
            if (operationLogService != null) {
                try {
                    log.debug("Calling operationLogService.save() for: {}", record.getOperation());
                    operationLogService.save(record);
                } catch (Exception e) {
                    log.error("Failed to save operation log: {}", e.getMessage());
                }
            } else {
                log.warn("OperationLogService is null, cannot save log");
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
