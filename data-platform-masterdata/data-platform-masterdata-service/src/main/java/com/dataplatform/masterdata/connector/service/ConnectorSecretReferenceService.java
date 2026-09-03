package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.api.dto.ConnectorSecretReferenceOptionDTO;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        Set<String> refs = new LinkedHashSet<>();
        if (secretRefs != null) {
            for (String ref : secretRefs) {
                if (!StringUtils.hasText(ref) || !ref.equals(ref.trim()) || ref.length() > 256) {
                    throw new IllegalArgumentException("秘密引用格式无效");
                }
                refs.add(ref);
            }
        }
        if (refs.size() > 64) throw new IllegalArgumentException("单步骤秘密引用不能超过64个");
        Set<String> availableRefs = listAvailableReferences(vendorConfigId).stream()
                .map(ConnectorSecretReferenceOptionDTO::secretRef)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        VendorInfo vendor = vendorMapper.selectById(config.getVendorId());
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String ref : refs) {
            if (!availableRefs.contains(ref)) {
                throw new IllegalArgumentException("秘密引用不存在或不属于当前厂商: " + ref);
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

    /**
     * Lists only references that can currently be selected by a connector form.
     * The returned DTO has no value field and this method never resolves a secret.
     */
    public List<ConnectorSecretReferenceOptionDTO> listAvailableReferences(Long vendorConfigId) {
        VendorConfig config = configMapper.selectById(vendorConfigId);
        if (config == null || config.getVendorId() == null) {
            throw new IllegalArgumentException("厂商配置不存在或未绑定厂商");
        }

        List<ConnectorSecretReferenceOptionDTO> result = new ArrayList<>();
        VendorInfo vendor = vendorMapper.selectById(config.getVendorId());
        if (vendor != null && StringUtils.hasText(vendor.getSecretKey())) {
            result.add(new ConnectorSecretReferenceOptionDTO("vendor.secretKey", "VENDOR", true));
        }
        List<com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig> configs =
                extendedConfigService.getByVendor(config.getVendorId());
        if (configs != null) {
            configs.stream()
                    .filter(item -> item != null && StringUtils.hasText(item.getConfigKey())
                            && Boolean.TRUE.equals(item.getIsEncrypted()))
                    .map(item -> new ConnectorSecretReferenceOptionDTO(
                            item.getConfigKey().trim(), "VENDOR", true))
                    .forEach(result::add);
        }
        return result.stream()
                .distinct()
                .sorted(Comparator.comparing(ConnectorSecretReferenceOptionDTO::secretRef))
                .toList();
    }
}
