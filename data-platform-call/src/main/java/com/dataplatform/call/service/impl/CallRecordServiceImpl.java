package com.dataplatform.call.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.mapper.CallRecordMapper;
import com.dataplatform.call.service.CallRecordService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class CallRecordServiceImpl extends ServiceImpl<CallRecordMapper, CallRecord> 
    implements CallRecordService {

    @Override
    public String recordCall(CallRecord record) {
        if (record.getRequestId() == null) {
            record.setRequestId("req_" + IdUtil.fastSimpleUUID());
        }
        if (record.getCallTime() == null) {
            record.setCallTime(LocalDateTime.now());
        }
        save(record);
        return record.getRequestId();
    }

    @Override
    public Page<CallRecord> pageQuery(Long tenantId, Long callerId, String vendorCode,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       Integer page, Integer pageSize) {
        Page<CallRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        
        if (tenantId != null) {
            wrapper.eq(CallRecord::getTenantId, tenantId);
        }
        if (callerId != null) {
            wrapper.eq(CallRecord::getCallerId, callerId);
        }
        if (vendorCode != null) {
            wrapper.eq(CallRecord::getVendorCode, vendorCode);
        }
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }
        
        wrapper.orderByDesc(CallRecord::getCallTime);
        return page(pageParam, wrapper);
    }

    @Override
    public Map<String, Object> getStats(Long tenantId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(CallRecord::getTenantId, tenantId);
        }
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }
        
        Long totalCount = count(wrapper);
        wrapper.eq(CallRecord::getSuccess, true);
        Long successCount = count(wrapper);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("successCount", successCount);
        stats.put("failCount", totalCount - successCount);
        stats.put("successRate", totalCount > 0 ? (double) successCount / totalCount * 100 : 0);
        
        return stats;
    }
}
