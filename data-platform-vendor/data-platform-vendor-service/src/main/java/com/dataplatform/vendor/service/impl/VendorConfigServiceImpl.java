package com.dataplatform.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.vendor.entity.DataType;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.mapper.DataTypeMapper;
import com.dataplatform.vendor.mapper.VendorConfigMapper;
import com.dataplatform.vendor.mapper.VendorInfoMapper;
import com.dataplatform.vendor.service.VendorConfigService;
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
    private static final long CACHE_TTL_SECONDS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Autowired
    private ApiInterfaceFeignClient apiInterfaceFeignClient;

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
        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .eq(VendorConfig::getDataTypeCode, dataType)
            .eq(VendorConfig::getStatus, StatusConstants.ACTIVE));
    }

    @Override
    public VendorConfig getByVendorCodeAndDataTypeCode(String vendorCode, String dataTypeCode) {
        VendorInfo vendorInfo = getVendorInfoByCode(vendorCode);
        if (vendorInfo == null) {
            return null;
        }

        DataType dataType = dataTypeMapper.selectOne(
            new LambdaQueryWrapper<DataType>()
                .eq(DataType::getDataTypeCode, dataTypeCode)
        );
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

        ApiInterfaceDTO apiInterface = apiInterfaceFeignClient.getByInterfaceCode(interfaceCode);
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
        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .eq(VendorConfig::getDataTypeCode, dataTypeCode)
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

    private VendorInfo getVendorInfoByCode(String vendorCode) {
        if (!StringUtils.hasText(vendorCode)) {
            return null;
        }

        String cacheKey = VENDOR_INFO_CACHE_PREFIX + vendorCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, VendorInfo.class);
            } catch (Exception e) {
                // Cache parse error, fall through to DB
            }
        }

        VendorInfo vendorInfo = vendorInfoMapper.selectOne(
            new LambdaQueryWrapper<VendorInfo>()
                .eq(VendorInfo::getVendorCode, vendorCode)
                .eq(VendorInfo::getStatus, StatusConstants.ACTIVE)
        );

        if (vendorInfo != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(vendorInfo),
                    CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Cache write error, ignore
            }
        }

        return vendorInfo;
    }
}
