package com.dataplatform.call.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.call.entity.CallRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CallRecordService extends IService<CallRecord> {
    
    String recordCall(CallRecord record);
    
    Page<CallRecord> pageQuery(Long tenantId, Long callerId, String vendorCode, 
                                LocalDateTime startTime, LocalDateTime endTime,
                                Integer page, Integer pageSize);
    
    Map<String, Object> getStats(Long tenantId, LocalDateTime startTime, LocalDateTime endTime);
}
