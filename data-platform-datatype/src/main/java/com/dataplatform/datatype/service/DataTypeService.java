package com.dataplatform.datatype.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.datatype.entity.DataType;
import com.dataplatform.datatype.mapper.DataTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataTypeService extends ServiceImpl<DataTypeMapper, DataType> {

    public PageResult<DataType> list(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(DataType::getDataTypeName, keyword)
                   .or()
                   .like(DataType::getDataTypeCode, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DataType::getStatus, status);
        }
        wrapper.eq(DataType::getDeleted, false);
        wrapper.orderByDesc(DataType::getCreatedAt);

        Page<DataType> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<DataType> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }
}