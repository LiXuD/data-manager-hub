package com.dataplatform.masterdata.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.call.api.dto.CallStatsDTO;
import com.dataplatform.access.call.api.dto.DailyCallStatsDTO;
import com.dataplatform.access.call.api.feign.CallStatsInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;
import com.dataplatform.masterdata.interface_.mapper.ApiInterfaceMapper;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 主数据域接口定义的 Api Interface Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class ApiInterfaceServiceImpl extends ServiceImpl<ApiInterfaceMapper, ApiInterface> implements ApiInterfaceService {

    private static final int DEFAULT_SLA_THRESHOLD = 2000;
    private static final int DEFAULT_STATS_DAYS = 7;
    private static final int DEFAULT_DAILY_STATS_DAYS = 30;
    private static final String INTERFACE_CACHE_PREFIX = "interface:code:";
    private static final long INTERFACE_CACHE_TTL_SECONDS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CallStatsInternalFeignClient callStatsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VendorConfigService vendorConfigService;

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
    public List<ApiInterface> listOptions(Long vendorId, Long dataTypeId, String status) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) {
            wrapper.eq(ApiInterface::getVendorId, vendorId);
        }
        if (dataTypeId != null) {
            wrapper.eq(ApiInterface::getDataTypeId, dataTypeId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ApiInterface::getStatus, status);
        }
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
        return interfaceId != null && vendorConfigService.getByInterfaceId(interfaceId) != null;
    }

    @Override
    public boolean updateSchema(Long id, String requestSchema, String responseSchema) {
        ApiInterface apiInterface = this.getById(id);
        if (apiInterface == null) {
            return false;
        }

        return baseMapper.updateSchemaById(id, requestSchema, responseSchema) > 0;
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

        Result<CallStatsDTO> response = callStatsClient.getInterfaceStats(
                apiInterface.getInterfaceCode(), startTime.toString(), endTime.toString(), DEFAULT_SLA_THRESHOLD);
        CallStatsDTO stats = requireData(response, "接口调用统计");

        Map<String, Object> result = new HashMap<>();
        result.put("interfaceId", id);
        result.put("interfaceCode", apiInterface.getInterfaceCode());
        result.put("interfaceName", apiInterface.getInterfaceName());
        result.put("totalCalls", stats.getTotalCalls());
        result.put("successCalls", stats.getSuccessCalls());
        result.put("avgLatency", stats.getAvgLatency());
        result.put("slowCalls", stats.getSlowCalls());
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }

    @Override
    public List<Map<String, Object>> getDailyCallStats(Long id, LocalDateTime startTime, LocalDateTime endTime) {
        ApiInterface apiInterface = this.getById(id);
        if (apiInterface == null) {
            return List.of();
        }

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(DEFAULT_DAILY_STATS_DAYS);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        Result<List<DailyCallStatsDTO>> response = callStatsClient.getDailyInterfaceStats(
                apiInterface.getInterfaceCode(), startTime.toString(), endTime.toString());
        return requireData(response, "接口每日调用统计").stream()
                .map(this::toDailyStatsMap)
                .toList();
    }

    private Map<String, Object> toDailyStatsMap(DailyCallStatsDTO stats) {
        Map<String, Object> result = new HashMap<>();
        result.put("date", stats.getDate());
        result.put("total_calls", stats.getTotalCalls());
        result.put("success_calls", stats.getSuccessCalls());
        result.put("avg_latency", stats.getAvgLatency());
        return result;
    }

    private <T> T requireData(Result<T> response, String operation) {
        if (response == null || !Integer.valueOf(200).equals(response.getCode()) || response.getData() == null) {
            throw new IllegalStateException(operation + "服务调用失败");
        }
        return response.getData();
    }
}
