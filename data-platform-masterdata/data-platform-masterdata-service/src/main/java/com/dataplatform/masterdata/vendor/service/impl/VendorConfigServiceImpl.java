package com.dataplatform.masterdata.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.mapper.ApiInterfaceMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.connector.entity.VendorConnectorVersion;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorVersionMapper;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.dataplatform.masterdata.vendor.service.VendorConfigConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * 主数据域厂商的 Vendor Config Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
@RefreshScope
public class VendorConfigServiceImpl extends ServiceImpl<VendorConfigMapper, VendorConfig>
    implements VendorConfigService {

    private static final String SECRET_KEY_CACHE_PREFIX = "vendor:secret:";
    private static final String VENDOR_INFO_CACHE_PREFIX = "vendor:info:";
    private static final String DATA_TYPE_CACHE_PREFIX = "vendor:dataType:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${masterdata.vendor-config.cache-ttl-seconds:300}")
    private long cacheTtlSeconds = 300;

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Autowired
    private ApiInterfaceMapper apiInterfaceMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VendorConnectorVersionMapper connectorVersionMapper;

    @Override
    public List<VendorConfig> listByVendor(Long vendorId) {
        return list(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE)
            .orderByAsc(VendorConfig::getId));
    }

    @Override
    public VendorConfig getByVendorAndDataType(Long vendorId, String dataType) {
        DataType dataTypeEntity = getDataTypeByCode(dataType);
        if (dataTypeEntity == null) {
            return null;
        }

        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .eq(VendorConfig::getDataTypeId, dataTypeEntity.getId())
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE));
    }

    @Override
    public VendorConfig getByVendorCodeAndDataTypeCode(String vendorCode, String dataTypeCode) {
        VendorInfo vendorInfo = getVendorInfoByCode(vendorCode);
        if (vendorInfo == null) {
            return null;
        }

        DataType dataType = getDataTypeByCode(dataTypeCode);
        if (dataType == null) {
            return null;
        }

        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorInfo.getId())
            .eq(VendorConfig::getDataTypeId, dataType.getId())
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE)
        );
    }

    @Override
    public VendorConfig getByInterfaceId(Long interfaceId) {
        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getInterfaceId, interfaceId)
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE)
        );
    }

    @Override
    public List<VendorConfig> listByInterfaceId(Long interfaceId) {
        return list(new LambdaQueryWrapper<VendorConfig>()
                .eq(VendorConfig::getInterfaceId, interfaceId)
                .eq(VendorConfig::getDeleted, false)
                .orderByAsc(VendorConfig::getId));
    }

    @Override
    public Map<Long, List<VendorConfig>> listByInterfaceIds(Collection<Long> interfaceIds) {
        if (interfaceIds == null || interfaceIds.isEmpty()) {
            return Map.of();
        }
        List<VendorConfig> configs = list(new LambdaQueryWrapper<VendorConfig>()
                .in(VendorConfig::getInterfaceId, interfaceIds)
                .eq(VendorConfig::getDeleted, false)
                .orderByAsc(VendorConfig::getId));
        return configs.stream().collect(Collectors.groupingBy(
                VendorConfig::getInterfaceId, HashMap::new, Collectors.toList()));
    }

    @Override
    public VendorConfig getByInterfaceAndVendor(Long interfaceId, Long vendorId) {
        return getOne(new LambdaQueryWrapper<VendorConfig>()
                .eq(VendorConfig::getInterfaceId, interfaceId)
                .eq(VendorConfig::getVendorId, vendorId)
                .eq(VendorConfig::getDeleted, false), false);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public VendorConfig createBinding(VendorConfig config) {
        if (config == null || config.getInterfaceId() == null || config.getVendorId() == null) {
            throw new IllegalArgumentException("接口和厂商不能为空");
        }
        VendorInfo vendor = vendorInfoMapper.selectById(config.getVendorId());
        if (vendor == null || !CommonStatus.ACTIVE.equals(vendor.getStatus())) {
            throw new IllegalArgumentException("厂商不存在或未启用");
        }
        ApiInterface apiInterface = apiInterfaceMapper.selectById(config.getInterfaceId());
        if (apiInterface == null) {
            throw new IllegalArgumentException("接口不存在");
        }
        if (config.getDataTypeId() == null || !config.getDataTypeId().equals(apiInterface.getDataTypeId())) {
            throw new IllegalArgumentException("厂商配置数据类型必须与接口数据类型一致");
        }
        DataType dataType = dataTypeMapper.selectById(config.getDataTypeId());
        if (dataType == null || !CommonStatus.ACTIVE.equals(dataType.getStatus())) {
            throw new IllegalArgumentException("数据类型不存在或未启用");
        }
        if (getByInterfaceAndVendor(config.getInterfaceId(), config.getVendorId()) != null) {
            throw new VendorConfigConflictException("当前接口已绑定该厂商");
        }
        if (!save(config) || config.getId() == null) {
            throw new IllegalStateException("厂商配置保存失败或未返回ID");
        }
        LambdaUpdateWrapper<ApiInterface> update = new LambdaUpdateWrapper<>();
        update.eq(ApiInterface::getId, config.getInterfaceId())
                .isNull(ApiInterface::getPrimaryVendorConfigId)
                .set(ApiInterface::getPrimaryVendorConfigId, config.getId());
        if (apiInterfaceMapper.update(null, update) < 0) {
            throw new IllegalStateException("接口主厂商路由更新失败");
        }
        VendorConfig persisted = getById(config.getId());
        if (persisted == null) {
            throw new IllegalStateException("厂商配置保存后无法读取");
        }
        return persisted;
    }

    @Override
    public VendorConfig getByVendorCodeAndInterfaceCode(String vendorCode, String interfaceCode) {
        VendorInfo vendorInfo = getVendorInfoByCode(vendorCode);
        if (vendorInfo == null) {
            return null;
        }

        ApiInterface apiInterface = apiInterfaceMapper.selectOne(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getInterfaceCode, interfaceCode)
                .eq(ApiInterface::getDeleted, false));
        if (apiInterface == null) {
            return null;
        }

        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorInfo.getId())
            .eq(VendorConfig::getInterfaceId, apiInterface.getId())
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE)
        );
    }

    @Override
    public VendorConfig getByVendorIdAndDataTypeCode(Long vendorId, String dataTypeCode) {
        DataType dataType = getDataTypeByCode(dataTypeCode);
        if (dataType == null) {
            return null;
        }

        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .eq(VendorConfig::getDataTypeId, dataType.getId())
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE));
    }

    @Override
    public Long getDataTypeIdByCode(String dataTypeCode) {
        DataType dataType = getDataTypeByCode(dataTypeCode);
        return dataType != null ? dataType.getId() : null;
    }

    @Override
    public String getVendorName(Long vendorId) {
        VendorInfo vendor = vendorId == null ? null : vendorInfoMapper.selectById(vendorId);
        return vendor == null ? null : vendor.getVendorName();
    }

    @Override
    public String getDataTypeName(Long dataTypeId) {
        DataType dataType = dataTypeId == null ? null : dataTypeMapper.selectById(dataTypeId);
        return dataType == null ? null : dataType.getDataTypeName();
    }

    @Override
    public Map<Long, String> getVendorNames(Collection<Long> vendorIds) {
        if (vendorIds == null || vendorIds.isEmpty()) {
            return Map.of();
        }
        return safeList(vendorInfoMapper.selectBatchIds(vendorIds)).stream()
                .collect(Collectors.toMap(VendorInfo::getId, VendorInfo::getVendorName, (left, right) -> left));
    }

    @Override
    public Map<Long, String> getDataTypeNames(Collection<Long> dataTypeIds) {
        if (dataTypeIds == null || dataTypeIds.isEmpty()) {
            return Map.of();
        }
        return safeList(dataTypeMapper.selectBatchIds(dataTypeIds)).stream()
                .collect(Collectors.toMap(DataType::getId, DataType::getDataTypeName, (left, right) -> left));
    }

    @Override
    public Map<Long, Boolean> canActivateConfigs(Collection<Long> vendorConfigIds) {
        if (vendorConfigIds == null || vendorConfigIds.isEmpty()) {
            return Map.of();
        }
        List<VendorConfig> configs = safeList(baseMapper.selectBatchIds(vendorConfigIds));
        List<Long> connectorVersionIds = configs.stream()
                .map(VendorConfig::getActiveConnectorVersionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, VendorConnectorVersion> versions = connectorVersionIds.isEmpty()
                ? Map.of()
                : safeList(connectorVersionMapper.selectBatchIds(connectorVersionIds))
                        .stream().collect(Collectors.toMap(VendorConnectorVersion::getId, Function.identity(), (left, right) -> left));
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : vendorConfigIds) {
            VendorConfig config = configs.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
            VendorConnectorVersion version = config == null || config.getActiveConnectorVersionId() == null
                    ? null : versions.get(config.getActiveConnectorVersionId());
            result.put(id, config != null
                    && CommonStatus.ACTIVE.equals(config.getStatus())
                    && "PLUGIN".equals(config.getRuntimeMode())
                    && version != null
                    && id.equals(version.getVendorConfigId())
                    && "ACTIVE".equals(version.getStatus()));
        }
        return result;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    @Override
    public boolean canActivate(Long vendorConfigId) {
        VendorConfig config = getById(vendorConfigId);
        if (config == null
                || !"PLUGIN".equals(config.getRuntimeMode())
                || config.getActiveConnectorVersionId() == null) {
            return false;
        }
        VendorConnectorVersion version = connectorVersionMapper.selectById(config.getActiveConnectorVersionId());
        return version != null
                && vendorConfigId.equals(version.getVendorConfigId())
                && "ACTIVE".equals(version.getStatus());
    }

    @Override
    public String getSecretKey(String vendorCode) {
        if (!StringUtils.hasText(vendorCode)) {
            return null;
        }

        String cacheKey = SECRET_KEY_CACHE_PREFIX + vendorCode;
        String cachedKey = redisTemplate.opsForValue().get(cacheKey);
        if (cachedKey != null) {
            return cachedKey;
        }

        VendorInfo vendorInfo = getVendorInfoByCode(vendorCode);
        if (vendorInfo != null && vendorInfo.getSecretKey() != null) {
            redisTemplate.opsForValue().set(cacheKey, vendorInfo.getSecretKey(),
                cacheTtlSeconds, TimeUnit.SECONDS);
            return vendorInfo.getSecretKey();
        }

        return null;
    }

    /**
     * 通用缓存查询方法
     */
    private <T> T getWithCache(String cacheKey, Class<T> type, java.util.function.Supplier<T> dbLoader) {
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, type);
            } catch (Exception e) {
                // Cache parse error, fall through to DB
            }
        }

        T entity = dbLoader.get();
        if (entity != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(entity),
                    cacheTtlSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Cache write error, ignore
            }
        }

        return entity;
    }

    private DataType getDataTypeByCode(String dataTypeCode) {
        if (!StringUtils.hasText(dataTypeCode)) {
            return null;
        }
        return getWithCache(DATA_TYPE_CACHE_PREFIX + dataTypeCode, DataType.class, () ->
            dataTypeMapper.selectOne(
                new LambdaQueryWrapper<DataType>()
                    .eq(DataType::getDataTypeCode, dataTypeCode)
                    .eq(DataType::getStatus, CommonStatus.ACTIVE)
                    .eq(DataType::getDeleted, false)
            )
        );
    }

    private VendorInfo getVendorInfoByCode(String vendorCode) {
        if (!StringUtils.hasText(vendorCode)) {
            return null;
        }
        return getWithCache(VENDOR_INFO_CACHE_PREFIX + vendorCode, VendorInfo.class, () ->
            vendorInfoMapper.selectOne(
                new LambdaQueryWrapper<VendorInfo>()
                    .eq(VendorInfo::getVendorCode, vendorCode)
                    .eq(VendorInfo::getStatus, StatusConstants.ACTIVE)
            )
        );
    }
}
