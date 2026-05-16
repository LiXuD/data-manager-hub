package com.dataplatform.masterdata.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VendorConfigServiceImpl extends ServiceImpl<VendorConfigMapper, VendorConfig>
    implements VendorConfigService {

    private static final String SECRET_KEY_CACHE_PREFIX = "vendor:secret:";
    private static final String VENDOR_INFO_CACHE_PREFIX = "vendor:info:";
    private static final String DATA_TYPE_CACHE_PREFIX = "vendor:dataType:";
    private static final long CACHE_TTL_SECONDS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    @Autowired
    private StringRedisTemplate redisTemplate;

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
    public VendorConfig getByVendorCodeAndInterfaceCode(String vendorCode, String interfaceCode) {
        VendorInfo vendorInfo = getVendorInfoByCode(vendorCode);
        if (vendorInfo == null) {
            return null;
        }

        ApiInterface apiInterface = apiInterfaceService.getByInterfaceCode(interfaceCode);
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
                CACHE_TTL_SECONDS, TimeUnit.SECONDS);
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
                    CACHE_TTL_SECONDS, TimeUnit.SECONDS);
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
