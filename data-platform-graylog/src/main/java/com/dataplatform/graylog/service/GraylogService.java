package com.dataplatform.graylog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.common.pojo.PageResponse;
import com.dataplatform.graylog.entity.GrayRule;
import com.dataplatform.graylog.mapper.GrayRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GraylogService {
    @Autowired
    private GrayRuleMapper ruleMapper;

    public PageResponse<GrayRule> list(String serviceName, String status, int page, int pageSize) {
        LambdaQueryWrapper<GrayRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(serviceName)) {
            wrapper.like(GrayRule::getServiceName, serviceName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(GrayRule::getStatus, status);
        }
        wrapper.orderByDesc(GrayRule::getCreatedAt);
        
        Page<GrayRule> result = ruleMapper.selectPage(new Page<>(page, pageSize), wrapper);
        PageResponse<GrayRule> response = new PageResponse<>();
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        return response;
    }

    public GrayRule getById(Long id) {
        return ruleMapper.selectById(id);
    }

    public GrayRule create(GrayRule rule) {
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        return rule;
    }

    public GrayRule update(GrayRule rule) {
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return rule;
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    public GrayRule getActiveRule(String serviceName) {
        LambdaQueryWrapper<GrayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GrayRule::getServiceName, serviceName);
        wrapper.eq(GrayRule::getStatus, "active");
        return ruleMapper.selectOne(wrapper);
    }
}
