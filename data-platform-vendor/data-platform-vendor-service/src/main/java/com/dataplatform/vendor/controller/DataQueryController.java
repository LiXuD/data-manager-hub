package com.dataplatform.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.common.adapter.VendorAdapterConfig;
import com.dataplatform.common.adapter.VendorAdapterFactory;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.vendor.entity.DataType;
import com.dataplatform.vendor.entity.VendorConfig;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.mapper.DataTypeMapper;
import com.dataplatform.vendor.mapper.VendorConfigMapper;
import com.dataplatform.vendor.mapper.VendorInfoMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dataplatform.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VendorConfigMapper vendorConfigMapper;

    @Autowired
    private VendorInfoMapper vendorInfoMapper;

    @Autowired
    private DataTypeMapper dataTypeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        // interfaceCode 是接口编码(如 "interface_001")，需要先查 api_interface 表获取 interface_id(bigint)
        if (interfaceCode != null && !interfaceCode.isEmpty()) {
            List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM api_interface WHERE interface_code = ? AND deleted = false",
                Long.class, interfaceCode);
            if (!ids.isEmpty()) {
                configWrapper.eq(VendorConfig::getInterfaceId, ids.get(0));
            }
        }

        VendorConfig config = vendorConfigMapper.selectOne(configWrapper);
        if (config == null) {
            return errorResult("CONFIG_NOT_FOUND", "未找到匹配的接口配置");
        }

        // 4. 映射为 VendorAdapterConfig
        VendorAdapterConfig adapterConfig = buildAdapterConfig(config, vendor);

        // 5. 调用适配器
        return VendorAdapterFactory.getAdapter(vendorCode).execute(adapterConfig, params);
    }

    private VendorAdapterConfig buildAdapterConfig(VendorConfig config, VendorInfo vendor) {
        VendorAdapterConfig adapterConfig = new VendorAdapterConfig();
        adapterConfig.setVendorCode(vendor.getVendorCode());
        adapterConfig.setApiUrl(config.getApiUrl());
        adapterConfig.setMethod(config.getMethod());
        adapterConfig.setTimeout(config.getTimeout());
        adapterConfig.setRetryCount(config.getRetryCount());
        adapterConfig.setSignType(config.getSignType());
        adapterConfig.setSecretKey(vendor.getSecretKey());
        adapterConfig.setRequestTemplate(config.getRequestTemplate());
        adapterConfig.setResponseMapping(config.getResponseMapping());
        adapterConfig.setAuthType(config.getAuthType());

        // 解析 headerConfig (JSONB → Map<String, String>)
        if (config.getHeaderConfig() != null && !config.getHeaderConfig().isEmpty()) {
            try {
                Map<String, String> headers = objectMapper.readValue(config.getHeaderConfig(),
                    new TypeReference<Map<String, String>>() {});
                adapterConfig.setHeaders(headers);
            } catch (Exception e) {
                // ignore parse errors
            }
        }

        // 解析 authConfig (TEXT → Map<String, Object>)
        if (config.getAuthConfig() != null && !config.getAuthConfig().isEmpty()) {
            try {
                Map<String, Object> authConfig = objectMapper.readValue(config.getAuthConfig(),
                    new TypeReference<Map<String, Object>>() {});
                adapterConfig.setAuthConfig(authConfig);
            } catch (Exception e) {
                // ignore parse errors
            }
        }

        return adapterConfig;
    }

    private Map<String, Object> errorResult(String errorCode, String errorMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("errorCode", errorCode);
        result.put("errorMsg", errorMsg);
        return result;
    }
}
