package com.dataplatform.access.caller.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.access.caller.entity.CallerInfo;

import java.util.List;

/**
 * 访问域调用方的 Caller Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface CallerService extends IService<CallerInfo> {
    
    PageResult<CallerInfo> list(Integer page, Integer pageSize, String keyword, String status);
    
    List<CallerInfo> listByTenant(Long tenantId);
    
    CallerInfo getByCode(String callerCode);
}