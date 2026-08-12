package com.dataplatform.masterdata.interface_.service;

import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.RoutingReadiness;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.service.VendorConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the public and internal interface contract from one consistent routing view.
 * Batch inputs are resolved with bounded mapper calls so paged interface lists do not
 * perform one database lookup per row.
 */
@Component
public class ApiInterfaceDTOAssembler {

    private final VendorConfigService vendorConfigService;

    public ApiInterfaceDTOAssembler(VendorConfigService vendorConfigService) {
        this.vendorConfigService = vendorConfigService;
    }

    public ApiInterfaceDTO toDTO(ApiInterface entity) {
        if (entity == null) {
            return null;
        }
        return toDTOs(List.of(entity)).get(0);
    }

    public List<ApiInterfaceDTO> toDTOs(Collection<? extends ApiInterface> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<? extends ApiInterface> values = entities.stream().filter(Objects::nonNull).toList();
        Set<Long> interfaceIds = values.stream().map(ApiInterface::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<VendorConfig>> bindings = vendorConfigService.listByInterfaceIds(interfaceIds);

        Set<Long> configIds = bindings.values().stream().flatMap(Collection::stream)
                .map(VendorConfig::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        values.stream().map(ApiInterface::getPrimaryVendorConfigId).filter(Objects::nonNull).forEach(configIds::add);
        values.stream().map(ApiInterface::getFallbackVendorConfigId).filter(Objects::nonNull).forEach(configIds::add);
        Map<Long, Boolean> activation = vendorConfigService.canActivateConfigs(configIds);

        Set<Long> vendorIds = bindings.values().stream().flatMap(Collection::stream)
                .map(VendorConfig::getVendorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> vendorNames = vendorConfigService.getVendorNames(vendorIds);
        Set<Long> dataTypeIds = values.stream().map(ApiInterface::getDataTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> dataTypeNames = vendorConfigService.getDataTypeNames(dataTypeIds);

        return values.stream().map(entity -> toDTO(entity, bindings.getOrDefault(entity.getId(), List.of()),
                activation, vendorNames, dataTypeNames)).toList();
    }

    private ApiInterfaceDTO toDTO(ApiInterface entity, List<VendorConfig> configs,
                                  Map<Long, Boolean> activation,
                                  Map<Long, String> vendorNames,
                                  Map<Long, String> dataTypeNames) {
        ApiInterfaceDTO dto = new ApiInterfaceDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        if (dto.getDataTypeName() == null) {
            dto.setDataTypeName(dataTypeNames.get(entity.getDataTypeId()));
        }

        dto.setBindingCount(configs.size());
        dto.setPrimaryVendorConfigId(entity.getPrimaryVendorConfigId());
        dto.setFallbackVendorConfigId(entity.getFallbackVendorConfigId());
        VendorConfig primary = find(configs, entity.getPrimaryVendorConfigId());
        VendorConfig fallback = find(configs, entity.getFallbackVendorConfigId());
        dto.setPrimaryVendorName(primary == null ? null : vendorNames.get(primary.getVendorId()));
        dto.setFallbackVendorName(fallback == null ? null : vendorNames.get(fallback.getVendorId()));

        if (entity.getPrimaryVendorConfigId() == null) {
            dto.setRoutingReadiness(RoutingReadiness.UNBOUND);
        } else if (!Boolean.TRUE.equals(activation.get(entity.getPrimaryVendorConfigId()))) {
            dto.setRoutingReadiness(RoutingReadiness.PRIMARY_NOT_READY);
        } else if (entity.getFallbackVendorConfigId() != null
                && !Boolean.TRUE.equals(activation.get(entity.getFallbackVendorConfigId()))) {
            dto.setRoutingReadiness(RoutingReadiness.FALLBACK_NOT_READY);
        } else {
            dto.setRoutingReadiness(RoutingReadiness.READY);
        }
        return dto;
    }

    private VendorConfig find(List<VendorConfig> configs, Long id) {
        if (id == null) {
            return null;
        }
        return configs.stream().filter(config -> id.equals(config.getId())).findFirst().orElse(null);
    }
}
