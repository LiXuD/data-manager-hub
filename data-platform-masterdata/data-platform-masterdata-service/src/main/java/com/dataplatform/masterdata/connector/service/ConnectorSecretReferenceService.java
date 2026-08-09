package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Masterdata-owned authority for vendor-scoped connector secret references. */
@Service
public class ConnectorSecretReferenceService {
    private final VendorConfigMapper configMapper;
    private final VendorInfoMapper vendorMapper;
    private final VendorExtendedConfigService extendedConfigService;

    public ConnectorSecretReferenceService(VendorConfigMapper configMapper, VendorInfoMapper vendorMapper,
                                           VendorExtendedConfigService extendedConfigService) {
        this.configMapper = configMapper;
        this.vendorMapper = vendorMapper;
        this.extendedConfigService = extendedConfigService;
    }

    public boolean exists(Long vendorConfigId, String secretRef) {
        if (!StringUtils.hasText(secretRef)) return false;
        try {
            return resolve(vendorConfigId, Set.of(secretRef)).containsKey(secretRef);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Map<String, String> resolve(Long vendorConfigId, Set<String> secretRefs) {
        VendorConfig config = configMapper.selectById(vendorConfigId);
        if (config == null || config.getVendorId() == null) {
            throw new IllegalArgumentException("厂商配置不存在或未绑定厂商");
        }
        Set<String> refs = secretRefs == null ? Set.of() : Set.copyOf(secretRefs);
        if (refs.size() > 64) throw new IllegalArgumentException("单步骤秘密引用不能超过64个");
        VendorInfo vendor = vendorMapper.selectById(config.getVendorId());
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String ref : refs) {
            if (!StringUtils.hasText(ref) || ref.length() > 256) {
                throw new IllegalArgumentException("秘密引用格式无效");
            }
            String value = "vendor.secretKey".equals(ref)
                    ? vendor == null ? null : vendor.getSecretKey()
                    : extendedConfigService.getConfig(config.getVendorId(), ref);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("秘密引用不存在或不属于当前厂商: " + ref);
            }
            resolved.put(ref, value);
        }
        return Map.copyOf(resolved);
    }
}
