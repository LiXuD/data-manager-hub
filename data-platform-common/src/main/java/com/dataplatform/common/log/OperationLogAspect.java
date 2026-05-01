package com.dataplatform.common.log;

import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.util.IpUtil;
import com.dataplatform.common.util.UserContext;
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
    private static final int MAX_LOG_LENGTH = 8192;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        OperationLogRecord record = new OperationLogRecord();
        record.setCreatedAt(LocalDateTime.now());

        try {
            var signature = (org.aspectj.lang.reflect.MethodSignature) point.getSignature();
            record.setModule(operationLog.module());
            record.setOperation(operationLog.operation());
            record.setDescription(operationLog.description());
            record.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                record.setIp(IpUtil.getClientIp(request));
            }

            Long userId = UserContext.getCurrentUserId();
            if (userId != null) {
                record.setUserId(userId);
                record.setUsername(UserContext.getCurrentUsername());
            }

            if (operationLog.saveParams() && objectMapper != null) {
                try {
                    Object[] args = point.getArgs();
                    if (args != null && args.length > 0) {
                        record.setParams(truncateJson(objectMapper.writeValueAsString(args)));
                    }
                } catch (Exception ignored) {
                }
            }

            Object result = point.proceed();

            record.setStatus(StatusConstants.SUCCESS);
            if (operationLog.saveResult() && objectMapper != null && result != null) {
                try {
                    record.setResult(truncateJson(objectMapper.writeValueAsString(result)));
                } catch (Exception ignored) {
                }
            }

            return result;
        } catch (Throwable e) {
            record.setStatus(StatusConstants.FAIL);
            record.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            record.setDuration(System.currentTimeMillis() - startTime);
            if (operationLogService != null) {
                try {
                    operationLogService.save(record);
                } catch (Exception e) {
                    log.error("Failed to save operation log: {}", e.getMessage());
                }
            }
        }
    }

    private String truncateJson(String json) {
        if (json != null && json.length() > MAX_LOG_LENGTH) {
            return json.substring(0, MAX_LOG_LENGTH) + "...[truncated]";
        }
        return json;
    }
}
