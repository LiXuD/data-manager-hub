package com.dataplatform.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.vendor.entity.DataType;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.mapper.DataTypeMapper;
import com.dataplatform.vendor.mapper.VendorConfigMapper;
import com.dataplatform.vendor.mapper.VendorInfoMapper;
import com.dataplatform.vendor.service.VendorConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class VendorConfigServiceImpl extends ServiceImpl<VendorConfigMapper, VendorConfig>
    implements VendorConfigService {

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

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

    @Override
    public VendorConfig getByVendorCodeAndDataTypeCode(String vendorCode, String dataTypeCode) {
        // 1. 获取厂商ID
        VendorInfo vendorInfo = vendorInfoMapper.selectOne(
            new LambdaQueryWrapper<VendorInfo>()
                .eq(VendorInfo::getVendorCode, vendorCode)
                .eq(VendorInfo::getStatus, "active")
        );
        if (vendorInfo == null) {
            return null;
        }

        // 2. 获取数据类型ID
        DataType dataType = dataTypeMapper.selectOne(
            new LambdaQueryWrapper<DataType>()
                .eq(DataType::getDataTypeCode, dataTypeCode)
        );
        if (dataType == null) {
            return null;
        }

        // 3. 获取配置
        return getOne(new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendorInfo.getId())
            .eq(VendorConfig::getDataTypeId, dataType.getId())
            .eq(VendorConfig::getStatus, "active")
        );
    }
}
