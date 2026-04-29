package com.dataplatform.caller.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.caller.entity.CallerInfo;

import java.util.List;

public interface CallerService extends IService<CallerInfo> {
    
    PageResult<CallerInfo> list(Integer page, Integer pageSize, String keyword, String status);
    
    List<CallerInfo> listByTenant(Long tenantId);
    
    CallerInfo getByCode(String callerCode);
}