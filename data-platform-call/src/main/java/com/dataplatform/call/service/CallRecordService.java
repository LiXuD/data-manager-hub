package com.dataplatform.call.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.call.entity.CallRecord;

import java.time.LocalDateTime;
import java.util.Map;

public interface CallRecordService extends IService<CallRecord> {

    PageResult<CallRecord> list(Long callerId, Long vendorId, String dataType, Boolean success,
                                LocalDateTime startTime, LocalDateTime endTime,
                                int page, int pageSize);

    PageResult<CallRecord> query(Long callerId, String phoneNumber, int page, int pageSize);

    Map<String, Object> getStats(LocalDateTime startTime, LocalDateTime endTime);

    byte[] exportData(Long callerId, LocalDateTime startTime, LocalDateTime endTime, String format);
}