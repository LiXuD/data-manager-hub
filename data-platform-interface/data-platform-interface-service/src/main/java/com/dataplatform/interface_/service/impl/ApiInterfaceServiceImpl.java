package com.dataplatform.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.entity.ApiInterfaceVO;
import com.dataplatform.interface_.mapper.ApiInterfaceMapper;
import com.dataplatform.interface_.mapper.InterfaceStatsMapper;
import com.dataplatform.interface_.service.ApiInterfaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ApiInterfaceServiceImpl extends ServiceImpl<ApiInterfaceMapper, ApiInterface> implements ApiInterfaceService {

    private static final int DEFAULT_SLA_THRESHOLD = 2000;
    private static final int DEFAULT_STATS_DAYS = 7;
    private static final int DEFAULT_DAILY_STATS_DAYS = 30;
    private static final String INTERFACE_CACHE_PREFIX = "interface:code:";
    private static final long INTERFACE_CACHE_TTL_SECONDS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private InterfaceStatsMapper interfaceStatsMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public PageResult<ApiInterfaceVO> list(Long vendorId, Long dataTypeId, String status, int page, int pageSize) {
        Page<ApiInterfaceVO> pageParam = new Page<>(page, pageSize);
        IPage<ApiInterfaceVO> result = baseMapper.selectListWithNames(pageParam, vendorId, dataTypeId, status);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public List<ApiInterface> listByDataTypeId(Long dataTypeId) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getDataTypeId, dataTypeId);
        wrapper.eq(ApiInterface::getStatus, StatusConstants.ACTIVE);
        wrapper.eq(ApiInterface::getDeleted, false);
        wrapper.orderByAsc(ApiInterface::getSort);
        return this.list(wrapper);
    }

    @Override
    public ApiInterface getByInterfaceCode(String interfaceCode) {
        if (!StringUtils.hasText(interfaceCode)) {
            return null;
        }

        String cacheKey = INTERFACE_CACHE_PREFIX + interfaceCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ApiInterface.class);
            } catch (Exception e) {
                // Cache parse error, fall through to DB
            }
        }

        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getInterfaceCode, interfaceCode);
        wrapper.eq(ApiInterface::getDeleted, false);
        ApiInterface apiInterface = this.getOne(wrapper);

        if (apiInterface != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(apiInterface),
                    INTERFACE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Cache write error, ignore
            }
        }

        return apiInterface;
    }

    @Override
    public boolean hasApiConfig(Long interfaceId) {
        // 检查是否已配置API（需要查询vendor_config表）
        // 这里返回false，实际实现需要注入VendorConfigService
        return false;
    }

    @Override
    public Map<String, Object> getInterfaceSchema(Long id) {
        ApiInterface apiInterface = this.getById(id);
        if (apiInterface == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("interfaceCode", apiInterface.getInterfaceCode());
        result.put("interfaceName", apiInterface.getInterfaceName());
        result.put("requestSchema", apiInterface.getRequestSchema());
        result.put("responseSchema", apiInterface.getResponseSchema());
        return result;
    }

    @Override
    public boolean updateSchema(Long id, String requestSchema, String responseSchema) {
        ApiInterface apiInterface = this.getById(id);
        if (apiInterface == null) {
            return false;
        }

        ApiInterface update = new ApiInterface();
        update.setId(id);
        if (requestSchema != null) {
            update.setRequestSchema(requestSchema);
        }
        if (responseSchema != null) {
            update.setResponseSchema(responseSchema);
        }
        return this.updateById(update);
    }

    @Override
    public boolean validateSchema(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            return true;
        }

        try {
            objectMapper.readTree(schema);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> getCallStats(Long id, LocalDateTime startTime, LocalDateTime endTime) {
        ApiInterface apiInterface = this.getById(id);
        if (apiInterface == null) {
            return null;
        }

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(DEFAULT_STATS_DAYS);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        Map<String, Object> stats = interfaceStatsMapper.getStatsByInterfaceId(
            id, startTime, endTime, DEFAULT_SLA_THRESHOLD);

        Map<String, Object> result = new HashMap<>();
        result.put("interfaceId", id);
        result.put("interfaceCode", apiInterface.getInterfaceCode());
        result.put("interfaceName", apiInterface.getInterfaceName());
        result.put("totalCalls", stats.get("total_calls"));
        result.put("successCalls", stats.get("success_calls"));
        result.put("avgLatency", stats.get("avg_latency") != null
            ? ((Number) stats.get("avg_latency")).doubleValue()
            : 0);
        result.put("slowCalls", stats.get("slow_calls"));
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }

    @Override
    public List<Map<String, Object>> getDailyCallStats(Long id, LocalDateTime startTime, LocalDateTime endTime) {
        if (this.getById(id) == null) {
            return List.of();
        }

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(DEFAULT_DAILY_STATS_DAYS);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        return interfaceStatsMapper.getDailyStatsByInterfaceId(id, startTime, endTime);
    }
}
