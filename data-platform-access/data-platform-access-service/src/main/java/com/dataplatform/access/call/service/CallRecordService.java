package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.common.result.PageResult;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 访问域数据调用的 Call Record Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface CallRecordService extends IService<CallRecord> {

    PageResult<CallRecord> list(Long callerId, Long vendorId, String dataType, Boolean success,
                                LocalDateTime startTime, LocalDateTime endTime,
                                int page, int pageSize);

    PageResult<CallRecord> list(Long callerId, Long vendorId, String dataType, Boolean success,
                                String apiCode, String productCode, String sceneCode, Boolean cacheHit,
                                LocalDateTime startTime, LocalDateTime endTime,
                                int page, int pageSize);

    Map<String, Object> getStats(LocalDateTime startTime, LocalDateTime endTime);

    CallRecord findLatestReusableCache(String apiCode, String requestHash, Long callerId,
                                       LocalDateTime since, String cacheScope);

    Map<String, Object> getDimensionStats(Long callerId, String productCode, String sceneCode,
                                          String apiCode, String vendorCode, String dataType,
                                          Boolean cacheHit, LocalDateTime startTime,
                                          LocalDateTime endTime);

    String export(Long callerId, LocalDateTime startTime, LocalDateTime endTime);

    byte[] exportData(Long callerId, LocalDateTime startTime, LocalDateTime endTime);
}
