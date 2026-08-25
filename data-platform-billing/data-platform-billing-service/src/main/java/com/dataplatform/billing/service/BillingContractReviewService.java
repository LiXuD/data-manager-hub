package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.api.Result;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.mapper.BillingPlanMapper;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 检测已发布方案所绑定的响应契约是否发生变化。 */
@Service
public class BillingContractReviewService {

    private final BillingPlanMapper planMapper;
    private final ApiInterfaceFeignClient interfaceClient;
    private final BillingConfigCodec codec;

    public BillingContractReviewService(BillingPlanMapper planMapper,
                                        ApiInterfaceFeignClient interfaceClient,
                                        BillingConfigCodec codec) {
        this.planMapper = planMapper;
        this.interfaceClient = interfaceClient;
        this.codec = codec;
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void scheduledReview() {
        review();
    }

    @Transactional
    public Map<String, Object> review() {
        List<BillingPlan> plans = planMapper.selectList(new LambdaQueryWrapper<BillingPlan>()
                .in(BillingPlan::getStatus, "PUBLISHED", "ACTIVE", "NEEDS_REVIEW"));
        int initialized = 0;
        int changed = 0;
        int unavailable = 0;
        for (BillingPlan plan : plans) {
            Result<InterfaceContractDTO> result;
            try {
                result = interfaceClient.getContract(plan.getInterfaceId());
            } catch (RuntimeException exception) {
                unavailable++;
                continue;
            }
            InterfaceContractDTO contract = result != null ? result.getData() : null;
            if (contract == null) {
                unavailable++;
                continue;
            }
            String current = codec.sha256(contract.getResponseFields());
            if (plan.getContractFingerprint() == null || plan.getContractFingerprint().isBlank()) {
                plan.setContractFingerprint(current);
                plan.setUpdatedAt(LocalDateTime.now());
                planMapper.updateById(plan);
                initialized++;
            } else if (!current.equals(plan.getContractFingerprint())
                    && !"NEEDS_REVIEW".equals(plan.getStatus())) {
                plan.setStatus("NEEDS_REVIEW");
                plan.setUpdatedAt(LocalDateTime.now());
                planMapper.updateById(plan);
                changed++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checked", plans.size());
        summary.put("initialized", initialized);
        summary.put("needsReview", changed);
        summary.put("unavailable", unavailable);
        return summary;
    }
}
