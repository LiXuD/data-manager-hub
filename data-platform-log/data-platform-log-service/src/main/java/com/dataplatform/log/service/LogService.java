package com.dataplatform.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.log.OperationLogRecord;
import com.dataplatform.common.log.OperationLogService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.log.entity.OperationLog;
import com.dataplatform.log.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class LogService extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    public PageResult<OperationLog> list(String keyword, String module, String operation,
                                         String startTime, String endTime, int page, int pageSize) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(OperationLog::getUsername, keyword)
                   .or()
                   .like(OperationLog::getOperation, keyword);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (StringUtils.hasText(operation)) {
            wrapper.like(OperationLog::getOperation, operation);
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<OperationLog> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    public void saveLog(OperationLog log) {
        log.setCreatedAt(LocalDateTime.now());
        save(log);
    }

    @Override
    public void save(OperationLogRecord record) {
        OperationLog log = new OperationLog();
        log.setUserId(record.getUserId());
        log.setUsername(record.getUsername());
        log.setModule(record.getModule());
        log.setOperation(record.getOperation());
        log.setMethod(record.getMethod());
        log.setParams(record.getParams());
        log.setResult(record.getResult());
        log.setIp(record.getIp());
        log.setLocation(record.getLocation());
        log.setDuration(record.getDuration() != null ? record.getDuration().intValue() : null);
        log.setStatus(record.getStatus());
        log.setCreatedAt(record.getCreatedAt());
        save(log);
    }
}
