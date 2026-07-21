package com.dataplatform.billing.service;

import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.mapper.BillingEventMapper;
import com.dataplatform.billing.mapper.BillingPlanMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 包次和包周期的固定费用事件，定时生成且运行时惰性兜底。 */
@Service
public class BillingRecurringChargeService {

    private final BillingPlanMapper planMapper;
    private final BillingEventMapper eventMapper;
    private final BillingPlanService planService;
    private final BillingPeriodResolver periodResolver;
    private final BillingConfigCodec codec;

    public BillingRecurringChargeService(BillingPlanMapper planMapper,
                                         BillingEventMapper eventMapper,
                                         BillingPlanService planService,
                                         BillingPeriodResolver periodResolver,
                                         BillingConfigCodec codec) {
        this.planMapper = planMapper;
        this.eventMapper = eventMapper;
        this.planService = planService;
        this.periodResolver = periodResolver;
        this.codec = codec;
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void accrueScheduledPlans() {
        accrue(LocalDateTime.now());
    }

    @Transactional
    public int accrue(LocalDateTime at) {
        List<BillingPlan> plans = planMapper.selectRecurringPlans(at);
        int created = 0;
        for (BillingPlan plan : plans) {
            BillingPlanModel model = planService.get(plan.getId());
            LocalDate period = periodResolver.resolve(at, plan.getSettlementCycle(), plan.getTimezone());
            if (ensureRecurring(plan, model, period, at) != null) created++;
        }
        return created;
    }

    public BillingEvent ensureRecurring(BillingPlan plan, BillingPlanModel model,
                                        LocalDate period, LocalDateTime at) {
        if (!"PACKAGE_COUNT".equals(plan.getTemplateCode())
                && !"FLAT_PERIOD".equals(plan.getTemplateCode())) return null;
        String requestId = "RECURRING:" + plan.getId() + ":" + period;
        eventMapper.lockRequest(requestId);
        BillingEvent existing = eventMapper.selectByRequestId(requestId);
        if (existing != null) return null;

        BigDecimal fee = model.getPricing().getPackageFee() != null
                ? model.getPricing().getPackageFee() : BigDecimal.ZERO;
        BillingEvent event = new BillingEvent();
        event.setRequestId(requestId);
        event.setEventType("RECURRING_FEE");
        event.setPlanId(plan.getId());
        event.setPlanCode(plan.getPlanCode());
        event.setPlanVersion(plan.getVersion());
        event.setTemplateCode(plan.getTemplateCode());
        event.setAccountingPurpose(plan.getAccountingPurpose());
        event.setVendorId(plan.getVendorId());
        event.setVendorCode(plan.getVendorCode());
        event.setInterfaceId(plan.getInterfaceId());
        event.setInterfaceCode(plan.getInterfaceCode());
        event.setBillable(true);
        event.setQuantity(BigDecimal.ZERO);
        event.setUnit("PERIOD");
        event.setUsageBefore(BigDecimal.ZERO);
        event.setBaseAmount(fee);
        event.setAdjustmentAmount(BigDecimal.ZERO);
        event.setFinalAmount(fee);
        event.setCurrency(plan.getCurrency());
        event.setStatus("POSTED");
        event.setDecisionDetail("周期固定费用");
        event.setPricingSnapshot(codec.write(model.getPricing()));
        event.setBillingPeriod(period);
        event.setCallTime(at);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
        return event;
    }
}
