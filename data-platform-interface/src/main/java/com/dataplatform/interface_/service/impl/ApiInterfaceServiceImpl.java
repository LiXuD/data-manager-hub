package com.dataplatform.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.mapper.ApiInterfaceMapper;
import com.dataplatform.interface_.service.ApiInterfaceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ApiInterfaceServiceImpl extends ServiceImpl<ApiInterfaceMapper, ApiInterface> implements ApiInterfaceService {

    @Override
    public PageResult<ApiInterface> list(Long vendorId, Long dataTypeId, String status, int page, int pageSize) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();

        if (dataTypeId != null) {
            wrapper.eq(ApiInterface::getDataTypeId, dataTypeId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ApiInterface::getStatus, status);
        }
        wrapper.eq(ApiInterface::getDeleted, false);
        wrapper.orderByAsc(ApiInterface::getSort);
        wrapper.orderByDesc(ApiInterface::getCreatedAt);

        Page<ApiInterface> result = this.page(new Page<>(page, pageSize), wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public List<ApiInterface> listByDataTypeId(Long dataTypeId) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getDataTypeId, dataTypeId);
        wrapper.eq(ApiInterface::getStatus, StatusConstants.ACTIVE);
        wrapper.eq(ApiInterface::getDeleted, false);
        wrapper.orderByAsc(ApiInterface::getSort);
        return this.list(wrapper);
    }

    @Override
    public ApiInterface getByInterfaceCode(String interfaceCode) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getInterfaceCode, interfaceCode);
        wrapper.eq(ApiInterface::getDeleted, false);
        return this.getOne(wrapper);
    }

    @Override
    public boolean hasApiConfig(Long interfaceId) {
        // 检查是否已配置API（需要查询vendor_config表）
        // 这里返回false，实际实现需要注入VendorConfigService
        return false;
    }
}
