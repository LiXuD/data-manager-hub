package com.dataplatform.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.log.entity.OperationLog;
import com.dataplatform.log.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class LogService {
    @Autowired
    private OperationLogMapper logMapper;

    public PageResponse<OperationLog> list(String username, String module, String operation,
                                           String startTime, String endTime, int page, int pageSize) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLog::getUsername, username);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (StringUtils.hasText(operation)) {
            wrapper.like(OperationLog::getOperation, operation);
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        
        Page<OperationLog> result = logMapper.selectPage(new Page<>(page, pageSize), wrapper);
        PageResponse<OperationLog> response = new PageResponse<>();
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        return response;
    }

    public void save(OperationLog log) {
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }
}
