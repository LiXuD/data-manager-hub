package com.dataplatform.masterdata.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.call.api.dto.CallStatsDTO;
import com.dataplatform.access.call.api.dto.DailyCallStatsDTO;
import com.dataplatform.access.call.api.feign.CallStatsInternalFeignClient;
import com.dataplatform.api.Result;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;
import com.dataplatform.masterdata.interface_.api.dto.VendorRoutingUpdateReqDTO;
import com.dataplatform.masterdata.interface_.mapper.ApiInterfaceMapper;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.io.Serializable;
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
    private VendorConfigMapper vendorConfigMapper;

    @Autowired
    private VendorConnectorVersionMapper connectorVersionMapper;

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
    public ApiInterface updateVendorRouting(Long interfaceId, VendorRoutingUpdateReqDTO request) {
        if (interfaceId == null || request == null) {
            throw new IllegalArgumentException("接口和厂商路由不能为空");
        }
        ApiInterface apiInterface = getById(interfaceId);
        if (apiInterface == null) {
            throw new IllegalArgumentException("接口不存在");
        }
        Long primaryId = request.getPrimaryVendorConfigId();
        Long fallbackId = request.getFallbackVendorConfigId();
        if (primaryId != null && primaryId.equals(fallbackId)) {
            throw new IllegalArgumentException("主厂商和备用厂商不能相同");
        }
        validateRoutingConfig(interfaceId, primaryId, "主厂商配置");
        validateRoutingConfig(interfaceId, fallbackId, "备用厂商配置");

        LambdaUpdateWrapper<ApiInterface> update = new LambdaUpdateWrapper<>();
        update.eq(ApiInterface::getId, interfaceId)
                .set(ApiInterface::getPrimaryVendorConfigId, primaryId)
                .set(ApiInterface::getFallbackVendorConfigId, fallbackId);
        if (baseMapper.update(null, update) != 1) {
            throw new IllegalArgumentException("接口路由更新失败");
        }
        redisTemplate.delete(INTERFACE_CACHE_PREFIX + apiInterface.getInterfaceCode());
        return getById(interfaceId);
    }

    @Override
    public boolean assignPrimaryIfAbsent(Long interfaceId, Long vendorConfigId) {
        LambdaUpdateWrapper<ApiInterface> update = new LambdaUpdateWrapper<>();
        update.eq(ApiInterface::getId, interfaceId)
                .isNull(ApiInterface::getPrimaryVendorConfigId)
                .set(ApiInterface::getPrimaryVendorConfigId, vendorConfigId);
        boolean updated = baseMapper.update(null, update) == 1;
        if (updated) {
            evictInterfaceCache(getById(interfaceId));
        }
        return updated;
    }

    @Override
    public ApiInterface getByRoutingConfigId(Long vendorConfigId) {
        if (vendorConfigId == null) {
            return null;
        }
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getDeleted, false)
                .and(condition -> condition.eq(ApiInterface::getPrimaryVendorConfigId, vendorConfigId)
                        .or().eq(ApiInterface::getFallbackVendorConfigId, vendorConfigId));
        return getOne(wrapper, false);
    }

    @Override
    public boolean canActivate(Long interfaceId) {
        ApiInterface apiInterface = getById(interfaceId);
        if (apiInterface == null || apiInterface.getPrimaryVendorConfigId() == null
                || !canActivateConfig(apiInterface.getPrimaryVendorConfigId())) {
            return false;
        }
        return true;
    }

    private boolean canActivateConfig(Long vendorConfigId) {
        VendorConfig config = vendorConfigMapper.selectById(vendorConfigId);
        if (config == null || !CommonStatus.ACTIVE.equals(config.getStatus())
                || !"PLUGIN".equals(config.getRuntimeMode())
                || config.getActiveConnectorVersionId() == null) {
            return false;
        }
        VendorConnectorVersion version = connectorVersionMapper.selectById(config.getActiveConnectorVersionId());
        return version != null
                && vendorConfigId.equals(version.getVendorConfigId())
                && "ACTIVE".equals(version.getStatus());
    }

    private void validateRoutingConfig(Long interfaceId, Long configId, String role) {
        if (configId == null) {
            return;
        }
        VendorConfig config = vendorConfigMapper.selectById(configId);
        if (config == null || !interfaceId.equals(config.getInterfaceId())
                || Boolean.TRUE.equals(config.getDeleted())) {
            throw new IllegalArgumentException(role + "不存在、已删除或不属于当前接口");
        }
    }

    @Override
    public boolean hasApiConfig(Long interfaceId) {
        if (interfaceId == null) {
            return false;
        }
        return vendorConfigMapper.selectCount(new LambdaQueryWrapper<VendorConfig>()
                .eq(VendorConfig::getInterfaceId, interfaceId)
                .eq(VendorConfig::getStatus, StatusConstants.ACTIVE)) > 0;
    }

    @Override
    public boolean updateSchema(Long id, String requestSchema, String responseSchema) {
        ApiInterface apiInterface = this.getById(id);
        if (apiInterface == null) {
            return false;
        }

        boolean updated = baseMapper.updateSchemaById(id, requestSchema, responseSchema) > 0;
        if (updated) {
            evictInterfaceCache(apiInterface);
        }
        return updated;
    }

    @Override
    public boolean updateById(ApiInterface entity) {
        if (entity == null || entity.getId() == null) {
            return false;
        }
        ApiInterface existing = super.getById(entity.getId());
        boolean updated = super.updateById(entity);
        if (updated) {
            evictInterfaceCache(existing);
            evictInterfaceCache(entity);
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        ApiInterface existing = super.getById(id);
        boolean removed = super.removeById(id);
        if (removed) {
            evictInterfaceCache(existing);
        }
        return removed;
    }

    private void evictInterfaceCache(ApiInterface apiInterface) {
        if (apiInterface != null && StringUtils.hasText(apiInterface.getInterfaceCode())) {
            redisTemplate.delete(INTERFACE_CACHE_PREFIX + apiInterface.getInterfaceCode());
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
