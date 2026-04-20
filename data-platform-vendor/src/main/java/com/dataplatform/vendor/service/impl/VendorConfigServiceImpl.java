package com.dataplatform.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.mapper.VendorConfigMapper;
import com.dataplatform.vendor.service.VendorConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class VendorConfigServiceImpl extends ServiceImpl<VendorConfigMapper, VendorConfig> 
    implements VendorConfigService {

    @Override
    public List<VendorConfig> listByVendor(Long vendorId) {
        return list(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .eq(VendorConfig::getStatus, "active")
            .orderByAsc(VendorConfig::getId));
    }

    @Override
    public VendorConfig getByVendorAndDataType(Long vendorId, String dataType) {
        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorId)
            .likeRight(VendorConfig::getDataTypeId, dataType)
            .eq(VendorConfig::getStatus, "active"));
    }
}
