package com.dataplatform.access.caller.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.access.caller.entity.CallerInfo;
import com.dataplatform.access.caller.mapper.CallerInfoMapper;
import com.dataplatform.access.caller.service.CallerService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 访问域调用方的 Caller Service Impl。
 * <p>业务服务实现，承载本域核心流程编排和事务边界。</p>
 */
@Service
public class CallerServiceImpl extends ServiceImpl<CallerInfoMapper, CallerInfo> 
    implements CallerService {

    @Override
    public PageResult<CallerInfo> list(Integer page, Integer pageSize, String keyword, String status) {
        LambdaQueryWrapper<CallerInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(CallerInfo::getCallerName, keyword)
                   .or()
                   .like(CallerInfo::getCallerCode, keyword);
        }
        
        if (StringUtils.hasText(status)) {
            wrapper.eq(CallerInfo::getStatus, status);
        }
        
        wrapper.eq(CallerInfo::getDeleted, false);
        wrapper.orderByDesc(CallerInfo::getCreatedAt);
        
        Page<CallerInfo> pageInfo = new Page<>(page, pageSize);
        Page<CallerInfo> result = this.page(pageInfo, wrapper);
        
        PageResult<CallerInfo> pageResult = new PageResult<>();
        pageResult.setCode(200);
        pageResult.setMessage("success");
        pageResult.setData(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        
        return pageResult;
    }
    
    @Override
    public List<CallerInfo> listByTenant(Long tenantId) {
        return list(new LambdaQueryWrapper<CallerInfo>()
            .eq(CallerInfo::getTenantId, tenantId)
            .eq(CallerInfo::getStatus, "active")
            .orderByDesc(CallerInfo::getId));
    }

    @Override
    public CallerInfo getByCode(String callerCode) {
        return getOne(new LambdaQueryWrapper<CallerInfo>()
            .eq(CallerInfo::getCallerCode, callerCode)
            .eq(CallerInfo::getDeleted, false));
    }
}
