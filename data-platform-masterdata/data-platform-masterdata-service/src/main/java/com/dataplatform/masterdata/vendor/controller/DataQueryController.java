package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.service.VendorAdapterConfigAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据查询控制器
 * <p>
 * 接收前端数据查询测试页面的请求，根据配置将请求转发到外部厂商 API。
 * 复用 common 模块的 HttpVendorAdapter 进行 HTTP 调用。
 * </p>
 */
@RestController
@RequestMapping("/data")
public class DataQueryController {

    @Autowired
    private VendorConfigMapper vendorConfigMapper;

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Autowired
    private ApiInterfaceService apiInterfaceService;

    @Autowired
    private VendorAdapterConfigAssembler adapterConfigAssembler;

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, Object> request) {
        String vendorCode = (String) request.get("vendorCode");
        String dataTypeCode = (String) request.get("dataTypeCode");
        String interfaceCode = (String) request.get("interfaceCode");

        if (vendorCode == null || vendorCode.isEmpty()) {
            return errorResult("INVALID_PARAMS", "vendorCode不能为空");
        }
        if (dataTypeCode == null || dataTypeCode.isEmpty()) {
            return errorResult("INVALID_PARAMS", "dataTypeCode不能为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Collections.emptyMap());

        // 1. 查询厂商信息
        VendorInfo vendor = vendorInfoMapper.selectOne(
            new LambdaQueryWrapper<VendorInfo>()
                .eq(VendorInfo::getVendorCode, vendorCode)
                .eq(VendorInfo::getStatus, CommonStatus.ACTIVE));
        if (vendor == null) {
            return errorResult("VENDOR_NOT_FOUND", "厂商不存在或已禁用: " + vendorCode);
        }

        // 2. 查询数据类型
        DataType dataType = dataTypeMapper.selectOne(
            new LambdaQueryWrapper<DataType>()
                .eq(DataType::getDataTypeCode, dataTypeCode)
                .eq(DataType::getStatus, CommonStatus.ACTIVE));
        if (dataType == null) {
            return errorResult("DATATYPE_NOT_FOUND", "数据类型不存在或已禁用: " + dataTypeCode);
        }

        // 3. 查询配置
        LambdaQueryWrapper<VendorConfig> configWrapper = new LambdaQueryWrapper<VendorConfig>()
            .eq(VendorConfig::getVendorId, vendor.getId())
            .eq(VendorConfig::getDataTypeId, dataType.getId())
            .eq(VendorConfig::getStatus, CommonStatus.ACTIVE);

        // interfaceCode 是接口编码(如 "interface_001")，统一通过接口定义服务解析。
        if (interfaceCode != null && !interfaceCode.isEmpty()) {
            ApiInterface apiInterface = apiInterfaceService.getByInterfaceCode(interfaceCode);
            if (apiInterface == null) {
                return errorResult("INTERFACE_NOT_FOUND", "接口不存在或已删除: " + interfaceCode);
            }
            configWrapper.eq(VendorConfig::getInterfaceId, apiInterface.getId());
        }

        VendorConfig config = vendorConfigMapper.selectOne(configWrapper);
        if (config == null) {
            return errorResult("CONFIG_NOT_FOUND", "未找到匹配的接口配置");
        }

        // 4. 映射为 VendorAdapterConfig
        VendorAdapterConfig adapterConfig;
        try {
            adapterConfig = adapterConfigAssembler.build(config, vendor);
        } catch (IllegalArgumentException e) {
            return errorResult("INVALID_CONFIG", e.getMessage());
        }

        // 5. 调用适配器
        return VendorAdapterFactory.getAdapter(vendorCode).execute(adapterConfig, params);
    }

    private Map<String, Object> errorResult(String errorCode, String errorMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMsg);
        return result;
    }
}
