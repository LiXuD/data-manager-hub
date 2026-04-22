package com.dataplatform.call.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.enums.CallStatus;
import com.dataplatform.call.service.CallRecordService;
import com.dataplatform.call.service.DataQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DataQueryServiceImpl implements DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryServiceImpl.class);

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${data.query.cache.ttl:3600}")
    private int cacheTtl;

    @Value("${data.query.timeout:5000}")
    private int defaultTimeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 计费单价配置 (实际应从配置中心或数据库读取)
    private static final Map<String, BigDecimal> UNIT_PRICES = Map.of(
        "company_info", new BigDecimal("0.30"),
        "person_phone", new BigDecimal("0.15"),
        "id_card_verify", new BigDecimal("0.20")
    );

    @Override
    public Map<String, Object> queryData(String vendorCode, String dataType,
                                          Map<String, Object> params,
                                          Long callerId, String apiKey) {
        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();

        try {
            // 1. 检查缓存
            String cacheKey = buildCacheKey(vendorCode, dataType, params);
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                log.info("命中缓存: {}", cacheKey);
                Map<String, Object> result = objectMapper.readValue(cachedResult, Map.class);
                result.put("cached", true);
                result.put("latency", System.currentTimeMillis() - startTime);

                // 记录缓存命中
                recordCall(requestId, callerId, vendorCode, dataType, params,
                          result, true, System.currentTimeMillis() - startTime, BigDecimal.ZERO, true);
                return result;
            }

            // 2. 调用厂商API (这里用模拟实现)
            Map<String, Object> vendorResult = callVendorApi(vendorCode, dataType, params);

            // 3. 记录调用结果
            long latency = System.currentTimeMillis() - startTime;
            BigDecimal cost = calculateCost(dataType);

            boolean success = vendorResult.get("success") != null
                              && Boolean.TRUE.equals(vendorResult.get("success"));

            recordCall(requestId, callerId, vendorCode, dataType, params,
                      vendorResult, success, latency, cost, false);

            // 4. 存入缓存
            try {
                redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(vendorResult),
                    cacheTtl, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("缓存存储失败: {}", e.getMessage());
            }

            vendorResult.put("cached", false);
            vendorResult.put("latency", latency);
            vendorResult.put("requestId", requestId);

            return vendorResult;

        } catch (Exception e) {
            log.error("数据查询失败: vendor={}, type={}, error={}",
                     vendorCode, dataType, e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("requestId", requestId);

            // 记录失败调用
            recordCall(requestId, callerId, vendorCode, dataType, params,
                      errorResult, false, System.currentTimeMillis() - startTime,
                      BigDecimal.ZERO, false);

            return errorResult;
        }
    }

    /**
     * 批量查询
     */
    public Map<String, Object> batchQuery(String vendorCode, String dataType,
                                           List<Map<String, Object>> paramsList,
                                           Long callerId, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        String batchId = "batch_" + System.currentTimeMillis();

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (Map<String, Object> params : paramsList) {
            try {
                Map<String, Object> queryResult = queryData(vendorCode, dataType, params, callerId, apiKey);
                if (Boolean.TRUE.equals(queryResult.get("success"))) {
                    success++;
                } else {
                    failed++;
                }
                results.add(Map.of(
                    "requestId", queryResult.get("requestId"),
                    "success", queryResult.get("success") != null ? queryResult.get("success") : false,
                    "result", queryResult.get("data")
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

    /**
     * 调用厂商API (实际实现需要根据厂商配置进行HTTP调用)
     */
    private Map<String, Object> callVendorApi(String vendorCode, String dataType,
                                                Map<String, Object> params) {
        // TODO: 实际实现应该根据 vendorCode 获取厂商配置
        // 构建HTTP请求，调用厂商API，解析响应

        // 模拟响应
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", buildMockData(dataType, params));
        return result;
    }

    /**
     * 构建模拟数据
     */
    private Map<String, Object> buildMockData(String dataType, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestParams", params);

        switch (dataType) {
            case "company_info":
                data.put("companyName", params.get("companyName"));
                data.put("legalPerson", "张三");
                data.put("registeredCapital", 10000000);
                data.put("businessStatus", "存续");
                break;
            case "person_phone":
                data.put("name", params.get("name"));
                data.put("phone", "138****8000");
                break;
            case "id_card_verify":
                data.put("name", params.get("name"));
                data.put("idCard", params.get("idCard"));
                data.put("result", "MATCH");
                break;
            default:
                data.put("message", "数据查询成功");
        }

        return data;
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(String vendorCode, String dataType, Map<String, Object> params) {
        try {
            String paramsStr = objectMapper.writeValueAsString(params);
            String rawKey = vendorCode + ":" + dataType + ":" + paramsStr;
            return "data_cache:" + DigestUtil.md5Hex(rawKey);
        } catch (Exception e) {
            return "data_cache:" + params.hashCode();
        }
    }

    /**
     * 计算费用 - 使用本地配置
     */
    private BigDecimal calculateCost(String dataType) {
        return UNIT_PRICES.getOrDefault(dataType, BigDecimal.ZERO);
    }

    /**
     * 记录调用
     */
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

    /**
     * 清除缓存
     */
    public void clearCache(String vendorCode, String dataType, Map<String, Object> params) {
        String cacheKey = buildCacheKey(vendorCode, dataType, params);
        redisTemplate.delete(cacheKey);
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Object> getCacheStats() {
        Set<String> keys = redisTemplate.keys("data_cache:*");
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKeys", keys != null ? keys.size() : 0);
        return stats;
    }
}