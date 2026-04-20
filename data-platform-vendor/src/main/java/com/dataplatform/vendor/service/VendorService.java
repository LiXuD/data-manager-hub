package com.dataplatform.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.vendor.entity.VendorInfo;

public interface VendorService extends IService<VendorInfo> {
    PageResult<VendorInfo> list(Integer page, Integer pageSize, String keyword, String status);
}
