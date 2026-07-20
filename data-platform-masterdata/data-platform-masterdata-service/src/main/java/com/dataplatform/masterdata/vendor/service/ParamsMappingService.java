package com.dataplatform.masterdata.vendor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.masterdata.vendor.entity.VendorParamsMapping;
import com.dataplatform.masterdata.vendor.mapper.VendorParamsMappingMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 主数据域厂商的 Params Mapping Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class ParamsMappingService extends ServiceImpl<VendorParamsMappingMapper, VendorParamsMapping> {

    public Map<String, Object> transformRequest(Map<String, Object> inputParams, Long vendorConfigId) {
        LambdaQueryWrapper<VendorParamsMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorParamsMapping::getVendorConfigId, vendorConfigId);
        List<VendorParamsMapping> mappings = this.list(wrapper);

        Map<String, Object> result = new HashMap<>();

        for (VendorParamsMapping mapping : mappings) {
            String sourceField = mapping.getSourceField();
            String targetField = mapping.getTargetField();
            Object value = inputParams.get(sourceField);

            if (value == null) {
                if (Boolean.TRUE.equals(mapping.getRequired())) {
                    throw new IllegalArgumentException("必填参数缺失: " + sourceField);
                }
                if (StringUtils.hasText(mapping.getDefaultValue())) {
                    value = mapping.getDefaultValue();
                } else {
                    continue;
                }
            }

            if (StringUtils.hasText(mapping.getTransformExpr())) {
                value = applyTransform(value, mapping.getTransformExpr());
            }

            if (StringUtils.hasText(mapping.getValidationRule())) {
                validate(value, mapping.getValidationRule());
            }

            result.put(targetField, value);
        }

        return result;
    }

    public Map<String, Object> transformResponse(Map<String, Object> vendorResponse, Long vendorConfigId) {
        LambdaQueryWrapper<VendorParamsMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorParamsMapping::getVendorConfigId, vendorConfigId);
        List<VendorParamsMapping> mappings = this.list(wrapper);

        Map<String, Object> result = new HashMap<>();

        for (VendorParamsMapping mapping : mappings) {
            String sourceField = mapping.getTargetField();
            String targetField = mapping.getSourceField();
            Object value = vendorResponse.get(sourceField);
            result.put(targetField, value);
        }

        return result;
    }

    private Object applyTransform(Object value, String transformExpr) {
        if (value == null) return null;

        if (transformExpr.startsWith("uppercase:")) {
            String field = transformExpr.substring(10);
            return value.toString().toUpperCase();
        } else if (transformExpr.startsWith("lowercase:")) {
            return value.toString().toLowerCase();
        } else if (transformExpr.startsWith("trim")) {
            return value.toString().trim();
        } else if (transformExpr.startsWith("prefix:")) {
            String prefix = transformExpr.substring(7);
            return prefix + value;
        } else if (transformExpr.startsWith("suffix:")) {
            String suffix = transformExpr.substring(7);
            return value + suffix;
        } else if (transformExpr.startsWith("map:")) {
            return applyMapTransform(value, transformExpr.substring(4));
        }

        return value;
    }

    private Object applyMapTransform(Object value, String mapConfig) {
        String[] entries = mapConfig.split(",");
        for (String entry : entries) {
            String[] kv = entry.split("=");
            if (kv.length == 2 && kv[0].equals(value.toString())) {
                return kv[1];
            }
        }
        return value;
    }

    private void validate(Object value, String validationRule) {
        if (value == null) return;

        if (validationRule.startsWith("regex:")) {
            String regex = validationRule.substring(6);
            if (!Pattern.matches(regex, value.toString())) {
                throw new IllegalArgumentException("参数格式校验失败: " + value);
            }
        } else if (validationRule.startsWith("range:")) {
            String range = validationRule.substring(6);
            String[] parts = range.split("-");
            if (parts.length == 2) {
                try {
                    double numValue = Double.parseDouble(value.toString());
                    double min = Double.parseDouble(parts[0]);
                    double max = Double.parseDouble(parts[1]);
                    if (numValue < min || numValue > max) {
                        throw new IllegalArgumentException("参数超出范围: " + value);
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("数值参数格式错误: " + value);
                }
            }
        } else if (validationRule.equals("not_empty")) {
            if (!StringUtils.hasText(value.toString())) {
                throw new IllegalArgumentException("参数不能为空");
            }
        }
    }

    public boolean saveMappings(Long vendorConfigId, List<VendorParamsMapping> mappings) {
        LambdaQueryWrapper<VendorParamsMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorParamsMapping::getVendorConfigId, vendorConfigId);
        this.remove(wrapper);

        for (VendorParamsMapping mapping : mappings) {
            mapping.setVendorConfigId(vendorConfigId);
        }
        return this.saveBatch(mappings);
    }
}