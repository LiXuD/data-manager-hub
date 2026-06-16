package com.dataplatform.masterdata.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;

import java.util.List;

public interface VendorService extends IService<VendorInfo> {
    PageResult<VendorInfo> list(Integer page, Integer pageSize, String keyword, String status);
    VendorInfo getByVendorCode(String vendorCode);
    List<VendorInfo> listAllActive();
}
