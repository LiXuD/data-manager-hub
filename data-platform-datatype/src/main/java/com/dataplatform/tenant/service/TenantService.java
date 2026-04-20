package com.dataplatform.datatype.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.datatype.entity.DataTypeInfo;

public interface DataTypeService extends IService<DataTypeInfo> {
    Page<DataTypeInfo> listPage(int page, int pageSize, String keyword, String status);
}