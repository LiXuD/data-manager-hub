package com.dataplatform.access.call.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingCalculateReqDTO;
import com.dataplatform.billing.api.dto.BillingCalculateRespDTO;
import com.dataplatform.billing.api.feign.BillingInternalFeignClient;
import com.dataplatform.common.entity.CallRecord;
import com.dataplatform.access.call.service.CallRecordService;
import com.dataplatform.access.call.service.DataQueryService;
import com.dataplatform.access.call.service.VendorProxyService;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 访问域数据调用的 Data Query Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class DataQueryServiceImpl implements DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryServiceImpl.class);

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private VendorProxyService vendorProxyService;

    @Autowired
    private VendorConfigInternalFeignClient vendorConfigFeignClient;

    @Autowired
    private BillingInternalFeignClient billingFeignClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${data.query.cache.ttl:3600}")
    private int cacheTtl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> queryData(String vendorCode, String dataType, String interfaceCode,
                                          Map<String, Object> params,
                                          Long callerId, String apiKey) {
        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();

        VendorConfigDTO config = null;
        if (interfaceCode != null && !interfaceCode.isEmpty()) {
            Result<VendorConfigDTO> configResult = vendorConfigFeignClient.getByVendorCodeAndInterfaceCode(vendorCode, interfaceCode);
            config = configResult != null ? configResult.getData() : null;
        }
        if (config == null && dataType != null && !dataType.isEmpty()) {
            Result<VendorConfigDTO> configResult = vendorConfigFeignClient.getByVendorCodeAndDataTypeCode(vendorCode, dataType);
            config = configResult != null ? configResult.getData() : null;
        }

        String effectiveDataType = (config != null && config.getDataTypeCode() != null)
            ? config.getDataTypeCode()
            : dataType;

        try {
            String cacheKey = buildCacheKey(vendorCode, effectiveDataType, params);
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                log.info("命中缓存: {}", cacheKey);
                Map<String, Object> result = objectMapper.readValue(cachedResult, Map.class);
                result.put("cached", true);
                result.put("latency", System.currentTimeMillis() - startTime);

                recordCall(requestId, callerId, vendorCode, effectiveDataType, params,
                          result, true, System.currentTimeMillis() - startTime,
                          BigDecimal.ZERO, true);
                return result;
            }

            Map<String, Object> vendorResult = vendorProxyService.callVendor(vendorCode, effectiveDataType, params, config);

            long latency = System.currentTimeMillis() - startTime;
            BigDecimal cost = calculateCost(requestId, callerId, vendorCode, effectiveDataType, latency);

            boolean success = Boolean.TRUE.equals(vendorResult.get("success"));

            recordCall(requestId, callerId, vendorCode, effectiveDataType, params,
                      vendorResult, success, latency, cost, false);

            if (success) {
                try {
                    redisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(vendorResult),
                        cacheTtl, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("缓存存储失败: {}", e.getMessage());
                }
            }

            vendorResult.put("cached", false);
            vendorResult.put("latency", latency);
            vendorResult.put("requestId", requestId);

            return vendorResult;

        } catch (Exception e) {
            log.error("数据查询失败: vendor={}, type={}, error={}",
                     vendorCode, effectiveDataType, e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorCode", "QUERY_ERROR");
            errorResult.put("errorMsg", e.getMessage());
            errorResult.put("requestId", requestId);

            recordCall(requestId, callerId, vendorCode, effectiveDataType, params,
                      errorResult, false, System.currentTimeMillis() - startTime,
                      BigDecimal.ZERO, false);

            return errorResult;
        }
    }

    @Override
    public Map<String, Object> batchQuery(String vendorCode, String dataType, String interfaceCode,
                                           List<Map<String, Object>> paramsList,
                                           Long callerId, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        String batchId = "batch_" + System.currentTimeMillis();

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (Map<String, Object> params : paramsList) {
            try {
                Map<String, Object> queryResult = queryData(vendorCode, dataType, interfaceCode, params, callerId, apiKey);
                if (Boolean.TRUE.equals(queryResult.get("success"))) {
                    success++;
                } else {
                    failed++;
                }
                results.add(Map.of(
                    "requestId", queryResult.getOrDefault("requestId", ""),
                    "success", queryResult.get("success") != null ? queryResult.get("success") : false,
                    "result", queryResult.getOrDefault("data", Collections.emptyMap())
                ));
            } catch (Exception e) {
                failed++;
                results.add(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
            }
        }

        result.put("batchId", batchId);
        result.put("total", paramsList.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("results", results);

        return result;
    }

    private BigDecimal calculateCost(String requestId, Long callerId, String vendorCode,
                                     String dataType, long latencyMs) {
        BillingCalculateReqDTO req = new BillingCalculateReqDTO();
        req.setVendorCode(vendorCode);
        req.setDataType(dataType);
        req.setCallCount(1);
        req.setLatency(latencyMs);
        req.setRequestId(requestId);
        req.setCallerId(callerId);
        req.setSuccess(true);
        req.setBillable(true);
        req.setCallTime(LocalDateTime.now());
        Result<BillingCalculateRespDTO> costResult = billingFeignClient.calculateCost(req);
        BillingCalculateRespDTO resp = costResult != null ? costResult.getData() : null;
        if (resp != null && resp.getCost() != null) {
            return resp.getCost();
        }
        throw new IllegalStateException("Billing service returned an empty cost");
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String buildCacheKey(String vendorCode, String dataType, Map<String, Object> params) {
        try {
            String paramsStr = objectMapper.writeValueAsString(params);
            String rawKey = vendorCode + ":" + dataType + ":" + paramsStr;
            return "data_cache:" + DigestUtil.md5Hex(rawKey);
        } catch (Exception e) {
            return "data_cache:" + params.hashCode();
        }
    }

    private void recordCall(String requestId, Long callerId, String vendorCode,
                           String dataType, Map<String, Object> params,
                           Map<String, Object> result, boolean success,
                           long latency, BigDecimal cost, boolean cached) {
        try {
            CallRecord record = new CallRecord();
            record.setRequestId(requestId);
            record.setCallerId(callerId);
            record.setVendorCode(vendorCode);
            record.setDataType(dataType);
            record.setRequestParams(objectMapper.writeValueAsString(params));
            record.setResponseData(objectMapper.writeValueAsString(result));
            record.setSuccess(success);
            record.setLatency((int) latency);
            record.setCost(cost);
            record.setCached(cached);
            record.setCallTime(LocalDateTime.now());

            callRecordService.save(record);
        } catch (Exception e) {
            log.error("记录调用失败: {}", e.getMessage());
        }
    }

    @Override
    public void clearCache(String vendorCode, String dataType, Map<String, Object> params) {
        String cacheKey = buildCacheKey(vendorCode, dataType, params);
        redisTemplate.delete(cacheKey);
    }

    @Override
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            long count = 0;
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match("data_cache:*")
                .count(100)
                .build();

            try (var cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    count++;
                }
            }
            stats.put("totalKeys", count);
        } catch (Exception e) {
            log.warn("获取缓存统计失败: {}", e.getMessage());
            stats.put("totalKeys", 0);
        }
        return stats;
    }
}
