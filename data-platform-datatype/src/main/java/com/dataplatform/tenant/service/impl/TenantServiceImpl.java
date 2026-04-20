package com.dataplatform.datatype.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.datatype.entity.DataTypeInfo;
import com.dataplatform.datatype.mapper.DataTypeMapper;
import com.dataplatform.datatype.service.DataTypeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataTypeServiceImpl extends ServiceImpl<DataTypeMapper, DataTypeInfo> implements DataTypeService {

    @Override
    public Page<DataTypeInfo> listPage(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<DataTypeInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(DataTypeInfo::getDataTypeCode, keyword)
                .or()
                .like(DataTypeInfo::getDataTypeName, keyword));
        }
        
        if (StringUtils.hasText(status)) {
            wrapper.eq(DataTypeInfo::getStatus, status);
        }
        
        wrapper.orderByDesc(DataTypeInfo::getCreatedAt);
        
        return page(new Page<>(page, pageSize), wrapper);
    }
}