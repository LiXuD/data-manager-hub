package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.common.result.PageResult;

import java.time.LocalDateTime;
import java.util.Map;

public interface CallRecordService extends IService<CallRecord> {

    PageResult<CallRecord> list(Long callerId, Long vendorId, String dataType, Boolean success,
                                LocalDateTime startTime, LocalDateTime endTime,
                                int page, int pageSize);

    Map<String, Object> getStats(LocalDateTime startTime, LocalDateTime endTime);

    String export(Long callerId, LocalDateTime startTime, LocalDateTime endTime);

    byte[] exportData(Long callerId, LocalDateTime startTime, LocalDateTime endTime);
}
