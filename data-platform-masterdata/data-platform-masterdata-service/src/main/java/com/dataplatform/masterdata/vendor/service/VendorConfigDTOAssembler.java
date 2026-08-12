package com.dataplatform.masterdata.vendor.service;

import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.mapper.ApiInterfaceMapper;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared, batch-based enrichment for public and internal VendorConfig responses. */
@Component
public class VendorConfigDTOAssembler {

    private final VendorInfoMapper vendorInfoMapper;
    private final DataTypeMapper dataTypeMapper;
    private final ApiInterfaceMapper apiInterfaceMapper;

    public VendorConfigDTOAssembler(VendorInfoMapper vendorInfoMapper,
                                    DataTypeMapper dataTypeMapper,
                                    ApiInterfaceMapper apiInterfaceMapper) {
        this.vendorInfoMapper = vendorInfoMapper;
        this.dataTypeMapper = dataTypeMapper;
        this.apiInterfaceMapper = apiInterfaceMapper;
    }

    public VendorConfigDTO toDTO(VendorConfig entity) {
        if (entity == null) {
            return null;
        }
        return toDTOs(List.of(entity)).get(0);
    }

    public List<VendorConfigDTO> toDTOs(Collection<VendorConfig> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<VendorConfig> values = entities.stream().filter(Objects::nonNull).toList();
        Set<Long> vendorIds = values.stream().map(VendorConfig::getVendorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        values.stream().map(VendorConfig::getFallbackVendorId)
                .filter(Objects::nonNull).forEach(vendorIds::add);
        Set<Long> dataTypeIds = values.stream().map(VendorConfig::getDataTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> interfaceIds = values.stream().map(VendorConfig::getInterfaceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, VendorInfo> vendors = safe(vendorInfoMapper.selectBatchIds(vendorIds)).stream()
                .collect(Collectors.toMap(VendorInfo::getId, item -> item, (left, right) -> left));
        Map<Long, DataType> dataTypes = safe(dataTypeMapper.selectBatchIds(dataTypeIds)).stream()
                .collect(Collectors.toMap(DataType::getId, item -> item, (left, right) -> left));
        Map<Long, ApiInterface> interfaces = safe(apiInterfaceMapper.selectBatchIds(interfaceIds)).stream()
                .collect(Collectors.toMap(ApiInterface::getId, item -> item, (left, right) -> left));

        return values.stream().map(entity -> toDTO(entity, vendors, dataTypes, interfaces)).toList();
    }

    private VendorConfigDTO toDTO(VendorConfig entity, Map<Long, VendorInfo> vendors,
                                  Map<Long, DataType> dataTypes,
                                  Map<Long, ApiInterface> interfaces) {
        VendorConfigDTO dto = new VendorConfigDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        VendorInfo vendor = vendors.get(entity.getVendorId());
        if (vendor != null) {
            dto.setVendorName(vendor.getVendorName());
        }
        DataType dataType = dataTypes.get(entity.getDataTypeId());
        if (dataType != null) {
            dto.setDataTypeName(dataType.getDataTypeName());
            dto.setDataTypeCode(dataType.getDataTypeCode());
        }
        VendorInfo fallback = vendors.get(entity.getFallbackVendorId());
        if (fallback != null) {
            dto.setFallbackVendorName(fallback.getVendorName());
        }
        ApiInterface apiInterface = interfaces.get(entity.getInterfaceId());
        if (apiInterface != null) {
            dto.setInterfaceName(apiInterface.getInterfaceName());
            if (entity.getId() != null && entity.getId().equals(apiInterface.getPrimaryVendorConfigId())) {
                dto.setRoutingRole("PRIMARY");
            } else if (entity.getId() != null && entity.getId().equals(apiInterface.getFallbackVendorConfigId())) {
                dto.setRoutingRole("FALLBACK");
            } else {
                dto.setRoutingRole("UNASSIGNED");
            }
        }
        return dto;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
