package com.dataplatform.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.vendor.entity.VendorInfo;
import com.dataplatform.vendor.mapper.VendorMapper;
import com.dataplatform.vendor.service.VendorService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class VendorServiceImpl extends ServiceImpl<VendorMapper, VendorInfo> implements VendorService {
    
    @Override
    public PageResult<VendorInfo> list(Integer page, Integer pageSize, String keyword, String status) {
        LambdaQueryWrapper<VendorInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.like(VendorInfo::getVendorName, keyword)
                   .or()
                   .like(VendorInfo::getVendorCode, keyword);
        }
        
        if (StringUtils.hasText(status)) {
            wrapper.eq(VendorInfo::getStatus, status);
        }
        
        wrapper.eq(VendorInfo::getDeleted, false);
        wrapper.orderByDesc(VendorInfo::getCreatedAt);
        
        Page<VendorInfo> pageInfo = new Page<>(page, pageSize);
        Page<VendorInfo> result = this.page(pageInfo, wrapper);
        
        // Build list data for PageResult (extends Result<List<T>>)
        List<VendorInfo> records = result.getRecords();
        
        PageResult<VendorInfo> pageResult = new PageResult<>();
        pageResult.setCode(0);
        pageResult.setMessage("success");
        pageResult.setData(records);
        pageResult.setTotal(Long.valueOf(result.getTotal()));
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        
        return pageResult;
    }
}