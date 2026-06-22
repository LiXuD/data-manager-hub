package com.dataplatform.common.log;

import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.util.IpUtil;
import com.dataplatform.common.util.LogTruncationUtil;
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

/**
 * 公共 Web 层操作日志的 Operation Log Aspect。
 * <p>日志治理组件，负责记录、转发或查询操作日志。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    @Autowired(required = false)
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
                        record.setParams(LogTruncationUtil.truncate(objectMapper.writeValueAsString(args), LogTruncationUtil.FULL));
                    }
                } catch (Exception ignored) {
                }
            }

            Object result = point.proceed();

            record.setStatus(StatusConstants.SUCCESS);
            if (operationLog.saveResult() && objectMapper != null && result != null) {
                try {
                    record.setResult(LogTruncationUtil.truncate(objectMapper.writeValueAsString(result), LogTruncationUtil.FULL));
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

}
