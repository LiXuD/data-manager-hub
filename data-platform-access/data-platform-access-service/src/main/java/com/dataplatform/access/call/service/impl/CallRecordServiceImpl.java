package com.dataplatform.access.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.call.mapper.CallRecordMapper;
import com.dataplatform.access.call.service.CallRecordService;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.common.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CallRecordServiceImpl extends ServiceImpl<CallRecordMapper, CallRecord>
    implements CallRecordService {

    @Override
    public PageResult<CallRecord> list(
            Long callerId, Long vendorId, String dataType, Boolean success,
            LocalDateTime startTime, LocalDateTime endTime,
            int page, int pageSize) {
        return list(callerId, vendorId, dataType, success, null, null, null, null,
                startTime, endTime, page, pageSize);
    }

    @Override
    public PageResult<CallRecord> list(
            Long callerId, Long vendorId, String dataType, Boolean success,
            String apiCode, String productCode, String sceneCode, Boolean cacheHit,
            LocalDateTime startTime, LocalDateTime endTime,
            int page, int pageSize) {

        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();

        if (callerId != null) {
            wrapper.eq(CallRecord::getCallerId, callerId);
        }
        if (vendorId != null) {
            wrapper.eq(CallRecord::getVendorId, vendorId);
        }
        if (StringUtils.hasText(dataType)) {
            wrapper.eq(CallRecord::getDataType, dataType);
        }
        if (success != null) {
            wrapper.eq(CallRecord::getSuccess, success);
        }
        if (StringUtils.hasText(apiCode)) {
            wrapper.eq(CallRecord::getApiCode, apiCode);
        }
        if (StringUtils.hasText(productCode)) {
            wrapper.eq(CallRecord::getProductCode, productCode);
        }
        if (StringUtils.hasText(sceneCode)) {
            wrapper.eq(CallRecord::getSceneCode, sceneCode);
        }
        if (cacheHit != null) {
            wrapper.eq(CallRecord::getCacheHit, cacheHit);
        }
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }

        wrapper.orderByDesc(CallRecord::getCallTime);
        Page<CallRecord> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<CallRecord> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @Override
    public Map<String, Object> getStats(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }

        Long totalCount = count(wrapper);

        LambdaQueryWrapper<CallRecord> successWrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            successWrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            successWrapper.le(CallRecord::getCallTime, endTime);
        }
        successWrapper.eq(CallRecord::getSuccess, true);
        Long successCount = count(successWrapper);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("successCount", successCount);
        stats.put("failCount", totalCount - successCount);
        stats.put("successRate", totalCount > 0 ? (double) successCount / totalCount * 100 : 0);

        return stats;
    }

    @Override
    public CallRecord findLatestReusableCache(String apiCode, String requestHash, Long callerId,
                                             LocalDateTime since, String cacheScope) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallRecord::getApiCode, apiCode)
                .eq(CallRecord::getRequestHash, requestHash)
                .eq(CallRecord::getSuccess, true)
                .ge(CallRecord::getCallTime, since);
        if ("CALLER".equalsIgnoreCase(cacheScope)) {
            wrapper.eq(CallRecord::getCallerId, callerId);
        }
        wrapper.orderByDesc(CallRecord::getCallTime).last("LIMIT 1");
        return getOne(wrapper, false);
    }

    @Override
    public Map<String, Object> getDimensionStats(Long callerId, String productCode, String sceneCode,
                                                 String apiCode, String vendorCode, String dataType,
                                                 Boolean cacheHit, LocalDateTime startTime,
                                                 LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        stats.putAll(firstStatsRow(queryDimensionStats(callerId, productCode, sceneCode, apiCode, vendorCode,
                dataType, cacheHit, startTime, endTime, Collections.emptyList())));
        stats.put("byCaller", queryDimensionStats(callerId, productCode, sceneCode, apiCode, vendorCode,
                dataType, cacheHit, startTime, endTime, List.of("caller_id")));
        stats.put("byCallerProduct", queryDimensionStats(callerId, productCode, sceneCode, apiCode, vendorCode,
                dataType, cacheHit, startTime, endTime, List.of("caller_id", "product_code")));
        stats.put("byScene", queryDimensionStats(callerId, productCode, sceneCode, apiCode, vendorCode,
                dataType, cacheHit, startTime, endTime, List.of("scene_code")));
        stats.put("byCallerProductScene", queryDimensionStats(callerId, productCode, sceneCode, apiCode, vendorCode,
                dataType, cacheHit, startTime, endTime, List.of("caller_id", "product_code", "scene_code")));
        return stats;
    }

    private List<Map<String, Object>> queryDimensionStats(Long callerId, String productCode, String sceneCode,
                                                          String apiCode, String vendorCode, String dataType,
                                                          Boolean cacheHit, LocalDateTime startTime,
                                                          LocalDateTime endTime, List<String> dimensions) {
        QueryWrapper<CallRecord> wrapper = buildDimensionStatsWrapper(callerId, productCode, sceneCode, apiCode,
                vendorCode, dataType, cacheHit, startTime, endTime);
        List<String> selects = new ArrayList<>(dimensions);
        selects.add("COUNT(*) AS \"totalCount\"");
        selects.add("SUM(CASE WHEN success THEN 1 ELSE 0 END) AS \"successCount\"");
        selects.add("SUM(CASE WHEN NOT success THEN 1 ELSE 0 END) AS \"failCount\"");
        selects.add("SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END) AS \"cacheHitCount\"");
        selects.add("SUM(CASE WHEN NOT cache_hit THEN 1 ELSE 0 END) AS \"realTimeCount\"");
        selects.add("COALESCE(SUM(cost), 0) AS \"totalCost\"");
        selects.add("COALESCE(AVG(duration_ms), 0) AS \"averageDurationMs\"");
        selects.add("COALESCE(MAX(duration_ms), 0) AS \"maxDurationMs\"");
        selects.add("COALESCE(MIN(duration_ms), 0) AS \"minDurationMs\"");
        wrapper.select(selects.toArray(new String[0]));
        if (!dimensions.isEmpty()) {
            wrapper.groupBy(dimensions);
        }
        return baseMapper.selectMaps(wrapper);
    }

    private QueryWrapper<CallRecord> buildDimensionStatsWrapper(Long callerId, String productCode, String sceneCode,
                                                                String apiCode, String vendorCode, String dataType,
                                                                Boolean cacheHit, LocalDateTime startTime,
                                                                LocalDateTime endTime) {
        QueryWrapper<CallRecord> wrapper = new QueryWrapper<>();
        wrapper.eq(callerId != null, "caller_id", callerId);
        wrapper.eq(StringUtils.hasText(productCode), "product_code", productCode);
        wrapper.eq(StringUtils.hasText(sceneCode), "scene_code", sceneCode);
        wrapper.eq(StringUtils.hasText(apiCode), "api_code", apiCode);
        wrapper.eq(StringUtils.hasText(vendorCode), "vendor_code", vendorCode);
        wrapper.eq(StringUtils.hasText(dataType), "data_type", dataType);
        wrapper.eq(cacheHit != null, "cache_hit", cacheHit);
        wrapper.ge(startTime != null, "call_time", startTime);
        wrapper.le(endTime != null, "call_time", endTime);
        return wrapper;
    }

    private Map<String, Object> firstStatsRow(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return Map.of(
                    "totalCount", 0,
                    "successCount", 0,
                    "failCount", 0,
                    "cacheHitCount", 0,
                    "realTimeCount", 0,
                    "totalCost", 0,
                    "averageDurationMs", 0,
                    "maxDurationMs", 0,
                    "minDurationMs", 0);
        }
        return rows.get(0);
    }

    @Override
    public String export(Long callerId, LocalDateTime startTime, LocalDateTime endTime) {
        return "/exports/call-record-" + System.currentTimeMillis() + ".csv";
    }

    @Override
    public byte[] exportData(Long callerId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        if (callerId != null) {
            wrapper.eq(CallRecord::getCallerId, callerId);
        }
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }
        List<CallRecord> records = list(wrapper);

        StringBuilder sb = new StringBuilder();
        sb.append("ID,CallerID,VendorID,DataType,Success,Latency,Cost,CallTime\n");
        for (CallRecord record : records) {
            sb.append(record.getId()).append(",");
            sb.append(record.getCallerId()).append(",");
            sb.append(record.getVendorId()).append(",");
            sb.append(record.getDataType()).append(",");
            sb.append(record.getSuccess()).append(",");
            sb.append(record.getLatency()).append(",");
            sb.append(record.getCost()).append(",");
            sb.append(record.getCallTime()).append("\n");
        }
        return sb.toString().getBytes();
    }
}
