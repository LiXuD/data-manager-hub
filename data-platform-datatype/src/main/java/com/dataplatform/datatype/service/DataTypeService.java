package com.dataplatform.datatype.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.datatype.entity.DataType;
import com.dataplatform.datatype.mapper.DataTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataTypeService {
    @Autowired
    private DataTypeMapper datatypeMapper;

    public PageResponse<DataType> list(String datatypeName, String status, int page, int pageSize) {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(datatypeName)) {
            wrapper.like(DataType::getDataTypeName, datatypeName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DataType::getStatus, status);
        }
        wrapper.eq(DataType::getDeleted, false);
        wrapper.orderByDesc(DataType::getCreatedAt);
        
        Page<DataType> result = datatypeMapper.selectPage(new Page<>(page, pageSize), wrapper);
        
        PageResponse<DataType> response = new PageResponse<>();
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        return response;
    }

    public DataType getById(Long id) {
        return datatypeMapper.selectById(id);
    }

    public void create(DataType datatype) {
        datatype.setCreatedAt(java.time.LocalDateTime.now());
        datatype.setDeleted(false);
        datatypeMapper.insert(datatype);
    }

    public void update(DataType datatype) {
        datatype.setUpdatedAt(java.time.LocalDateTime.now());
        datatypeMapper.updateById(datatype);
    }

    public void delete(Long id) {
        DataType datatype = new DataType();
        datatype.setId(id);
        datatype.setDeleted(true);
        datatypeMapper.updateById(datatype);
    }
}