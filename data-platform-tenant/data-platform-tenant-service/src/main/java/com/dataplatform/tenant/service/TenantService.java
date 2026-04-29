package com.dataplatform.tenant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.tenant.entity.TenantInfo;

public interface TenantService extends IService<TenantInfo> {
    Page<TenantInfo> listPage(int page, int pageSize, String keyword, String status);

    TenantInfo getByTenantCode(String tenantCode);
}