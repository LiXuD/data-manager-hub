package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.caller.entity.CallerProduct;
import com.dataplatform.access.caller.mapper.CallerProductMapper;
import com.dataplatform.common.constant.StatusConstants;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallerProductService extends ServiceImpl<CallerProductMapper, CallerProduct> {

    public CallerProduct getActiveProduct(Long callerId, String productCode) {
        if (callerId == null || productCode == null || productCode.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<CallerProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallerProduct::getCallerId, callerId)
                .eq(CallerProduct::getProductCode, productCode.trim())
                .eq(CallerProduct::getStatus, StatusConstants.ACTIVE)
                .last("LIMIT 1");
        return getOne(wrapper, false);
    }

    public List<CallerProduct> listByCaller(Long callerId) {
        LambdaQueryWrapper<CallerProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallerProduct::getCallerId, callerId)
                .orderByDesc(CallerProduct::getId);
        return list(wrapper);
    }

    @Transactional
    public CallerProduct saveProduct(Long callerId, CallerProduct product) {
        product.setId(null);
        product.setCallerId(callerId);
        if (product.getStatus() == null || product.getStatus().trim().isEmpty()) {
            product.setStatus(StatusConstants.ACTIVE);
        }
        if (product.getCacheScope() == null || product.getCacheScope().trim().isEmpty()) {
            product.setCacheScope("GLOBAL");
        }
        save(product);
        return product;
    }
}
