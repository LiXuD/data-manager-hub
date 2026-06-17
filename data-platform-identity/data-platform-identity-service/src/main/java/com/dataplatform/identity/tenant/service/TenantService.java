package com.dataplatform.identity.tenant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.identity.tenant.entity.TenantInfo;

/**
 * 身份租户域租户的 Tenant Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface TenantService extends IService<TenantInfo> {
    Page<TenantInfo> listPage(int page, int pageSize, String keyword, String status);

    TenantInfo getByTenantCode(String tenantCode);
}