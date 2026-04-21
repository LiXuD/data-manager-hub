package com.dataplatform.call.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.call.entity.CallRecord;
import com.dataplatform.call.mapper.CallRecordMapper;
import com.dataplatform.call.service.CallRecordService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class CallRecordServiceImpl extends ServiceImpl<CallRecordMapper, CallRecord>
    implements CallRecordService {

    @Override
    public PageResult<com.dataplatform.call.entity.CallRecord> list(
            Long callerId, Long vendorId, String dataType, Boolean success,
            LocalDateTime startTime, LocalDateTime endTime,
            int page, int pageSize) {

        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();

        if (callerId != null) {
            wrapper.eq(CallRecord::getCallerId, callerId);
        }
        if (vendorId != null) {
            wrapper.eq(CallRecord::getVendorId, vendorId);
        }
        if (StringUtils.hasText(dataType)) {
            wrapper.eq(CallRecord::getDataType, dataType);
        }
        if (success != null) {
            wrapper.eq(CallRecord::getSuccess, success);
        }
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }

        wrapper.orderByDesc(CallRecord::getCallTime);
        Page<CallRecord> result = this.page(new Page<>(page, pageSize), wrapper);

        PageResult<CallRecord> response = new PageResult<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(result.getRecords());
        response.setTotal(result.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        return response;
    }

    @Override
    public Map<String, Object> getStats(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            wrapper.ge(CallRecord::getCallTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(CallRecord::getCallTime, endTime);
        }

        Long totalCount = count(wrapper);
        LambdaQueryWrapper<CallRecord> successWrapper = new LambdaQueryWrapper<>(wrapper);
        successWrapper.eq(CallRecord::getSuccess, true);
        Long successCount = count(successWrapper);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("successCount", successCount);
        stats.put("failCount", totalCount - successCount);
        stats.put("successRate", totalCount > 0 ? (double) successCount / totalCount * 100 : 0);

        return stats;
    }

    @Override
    public String export(Long callerId, LocalDateTime startTime, LocalDateTime endTime) {
        // TODO: 实现导出功能
        return "/exports/call-record-" + System.currentTimeMillis() + ".csv";
    }
}