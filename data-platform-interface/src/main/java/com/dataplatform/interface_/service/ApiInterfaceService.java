package com.dataplatform.interface_.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.interface_.entity.ApiInterface;

import java.util.List;

public interface ApiInterfaceService extends IService<ApiInterface> {

    PageResult<ApiInterface> list(Long vendorId, Long dataTypeId, String status, int page, int pageSize);

    List<ApiInterface> listByDataTypeId(Long dataTypeId);

    ApiInterface getByInterfaceCode(String interfaceCode);

    boolean hasApiConfig(Long interfaceId);
}
