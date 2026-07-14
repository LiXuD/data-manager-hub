package com.dataplatform.masterdata.graylog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.enums.GrayRuleStatus;
import com.dataplatform.masterdata.graylog.entity.GrayRule;
import com.dataplatform.masterdata.graylog.mapper.GrayRuleMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 主数据域灰度规则的 Graylog Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class GraylogService extends ServiceImpl<GrayRuleMapper, GrayRule> {

    public PageResult<GrayRule> list(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<GrayRule> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(GrayRule::getRuleName, keyword)
                   .or()
                   .like(GrayRule::getServiceName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(GrayRule::getStatus, status);
        }
        wrapper.orderByDesc(GrayRule::getCreatedAt);

        Page<GrayRule> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<GrayRule> response = new PageResult<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    public GrayRule getActiveRule(String serviceName) {
        LambdaQueryWrapper<GrayRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GrayRule::getServiceName, serviceName);
        wrapper.eq(GrayRule::getStatus, GrayRuleStatus.ACTIVE.getCode());
        wrapper.and(w -> w.isNull(GrayRule::getStartTime)
                .or().le(GrayRule::getStartTime, LocalDateTime.now()));
        wrapper.and(w -> w.isNull(GrayRule::getEndTime)
                .or().ge(GrayRule::getEndTime, LocalDateTime.now()));
        return getOne(wrapper);
    }
}
