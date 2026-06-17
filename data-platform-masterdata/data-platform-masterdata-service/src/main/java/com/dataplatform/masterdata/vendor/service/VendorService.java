package com.dataplatform.masterdata.vendor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;

import java.util.List;

/**
 * 主数据域厂商的 Vendor Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface VendorService extends IService<VendorInfo> {
    PageResult<VendorInfo> list(Integer page, Integer pageSize, String keyword, String status);
    VendorInfo getByVendorCode(String vendorCode);
    List<VendorInfo> listAllActive();
}
