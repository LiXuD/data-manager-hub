package com.dataplatform.billing.service;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.api.dto.BillingChargeRespDTO;
import com.dataplatform.billing.api.dto.BillingAdditionalPlanDTO;
import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.mapper.BillingDailyMapper;
import com.dataplatform.billing.mapper.BillingEventMapper;
import com.dataplatform.billing.mapper.BillingUsageBalanceMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 计量判断、用量占用、价格计算和事件入账的强一致事务边界。 */
@Service
public class BillingChargeService {

    private final BillingPlanService planService;
    private final BillingMeteringEvaluator meteringEvaluator;
    private final BillingPricingEngine pricingEngine;
    private final BillingPeriodResolver periodResolver;
    private final BillingRecurringChargeService recurringChargeService;
    private final BillingEventMapper eventMapper;
    private final BillingUsageBalanceMapper usageMapper;
    private final BillingDailyMapper dailyMapper;
    private final BillingConfigCodec codec;

    public BillingChargeService(BillingPlanService planService,
                                BillingMeteringEvaluator meteringEvaluator,
                                BillingPricingEngine pricingEngine,
                                BillingPeriodResolver periodResolver,
                                BillingRecurringChargeService recurringChargeService,
                                BillingEventMapper eventMapper,
                                BillingUsageBalanceMapper usageMapper,
                                BillingDailyMapper dailyMapper,
                                BillingConfigCodec codec) {
        this.planService = planService;
        this.meteringEvaluator = meteringEvaluator;
        this.pricingEngine = pricingEngine;
        this.periodResolver = periodResolver;
        this.recurringChargeService = recurringChargeService;
        this.eventMapper = eventMapper;
        this.usageMapper = usageMapper;
        this.dailyMapper = dailyMapper;
        this.codec = codec;
    }

    @Transactional
    public BillingChargeRespDTO charge(BillingChargeReqDTO request) {
        validateRequest(request);
        BillingChargeRespDTO primary = chargeOne(request);
        Set<Long> chargedPlans = new HashSet<>();
        chargedPlans.add(request.getPlanId());
        List<BillingAdditionalPlanDTO> additionalPlans = request.getAdditionalPlans() != null
                ? request.getAdditionalPlans() : List.of();
        for (BillingAdditionalPlanDTO additional : additionalPlans) {
            validateAdditionalPlan(additional, chargedPlans);
            chargeOne(toAdditionalRequest(request, additional));
        }
        return primary;
    }

    private BillingChargeRespDTO chargeOne(BillingChargeReqDTO request) {
        eventMapper.lockRequest(request.getRequestId());
        BillingEvent existing = eventMapper.selectByRequestId(request.getRequestId());
        if (existing != null) return toResponse(existing);

        BillingPlan plan = planService.getEntity(request.getPlanId());
        BillingPlanModel model = planService.get(plan.getId());
        verifyPinnedPlan(plan, request);
        BillingMeteringEvaluator.Evaluation evaluation = meteringEvaluator.evaluate(model, request);
        LocalDateTime callTime = request.getCallTime() != null ? request.getCallTime() : LocalDateTime.now();
        LocalDate period = periodResolver.resolve(callTime, plan.getSettlementCycle(), plan.getTimezone());

        recurringChargeService.ensureRecurring(plan, model, period, callTime);

        BigDecimal usageBefore = BigDecimal.ZERO;
        if (evaluation.billable() && evaluation.quantity().signum() > 0) {
            String scopeKey = scopeKey(model, request);
            String lockKey = plan.getId() + ":" + period + ":" + scopeKey;
            usageMapper.lockBalance(lockKey);
            BigDecimal current = usageMapper.selectUsedQuantity(plan.getId(), period, scopeKey);
            usageBefore = current != null ? current : BigDecimal.ZERO;
            usageMapper.increment(plan.getId(), period, scopeKey, evaluation.quantity());
        }

        BillingPricingEngine.PricingResult pricing = evaluation.billable() && !evaluation.pendingReview()
                ? pricingEngine.calculate(model, evaluation.quantity(), usageBefore, request)
                : new BillingPricingEngine.PricingResult(BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        BillingEvent event = buildEvent(plan, model, request, evaluation, pricing, usageBefore, period, callTime);
        eventMapper.insert(event);
        aggregateDaily(event, request);
        return toResponse(event);
    }

    private void validateAdditionalPlan(BillingAdditionalPlanDTO additional, Set<Long> chargedPlans) {
        if (additional == null || additional.getPlanId() == null
                || additional.getPlanVersion() == null || !StringUtils.hasText(additional.getPolicyHash())) {
            throw new IllegalArgumentException("附加核算方案固定信息不完整");
        }
        if (!"INTERNAL_CHARGEBACK".equals(additional.getAccountingPurpose())) {
            throw new IllegalArgumentException("附加方案仅允许INTERNAL_CHARGEBACK计费方向");
        }
        if (!chargedPlans.add(additional.getPlanId())) {
            throw new IllegalArgumentException("同一计费方案不能重复入账");
        }
    }

    private BillingChargeReqDTO toAdditionalRequest(BillingChargeReqDTO source,
                                                     BillingAdditionalPlanDTO additional) {
        BillingChargeReqDTO target = new BillingChargeReqDTO();
        String suffix = ":CHARGEBACK:" + additional.getPlanId();
        String base = source.getRequestId();
        if (base.length() + suffix.length() > 128) {
            base = base.substring(0, 128 - suffix.length());
        }
        target.setRequestId(base + suffix);
        target.setPlanId(additional.getPlanId());
        target.setPlanVersion(additional.getPlanVersion());
        target.setPolicyHash(additional.getPolicyHash());
        target.setVendorCode(source.getVendorCode());
        target.setInterfaceCode(source.getInterfaceCode());
        target.setAccountingPurpose(additional.getAccountingPurpose());
        target.setDataType(source.getDataType());
        target.setTenantId(source.getTenantId());
        target.setCallerId(source.getCallerId());
        target.setVendorId(source.getVendorId());
        target.setCallTime(source.getCallTime());
        target.setSuccess(source.getSuccess());
        target.setCached(source.getCached());
        target.setResponseContractValid(source.getResponseContractValid());
        target.setLatencyMs(source.getLatencyMs());
        target.setHttpStatus(source.getHttpStatus());
        target.setMeteringFacts(additional.getMeteringFacts());
        return target;
    }

    private void validateRequest(BillingChargeReqDTO request) {
        if (request == null) throw new IllegalArgumentException("计费请求不能为空");
        if (!StringUtils.hasText(request.getRequestId()) || request.getRequestId().length() > 128) {
            throw new IllegalArgumentException("requestId不能为空且不能超过128位");
        }
        if (request.getPlanId() == null || request.getPlanVersion() == null) {
            throw new IllegalArgumentException("必须提交已解析的planId和planVersion");
        }
    }

    private void verifyPinnedPlan(BillingPlan plan, BillingChargeReqDTO request) {
        if (!plan.getVersion().equals(request.getPlanVersion())) {
            throw new IllegalArgumentException("计费方案版本不匹配");
        }
        if (!plan.getVendorCode().equals(request.getVendorCode())
                || !plan.getInterfaceCode().equals(request.getInterfaceCode())) {
            throw new IllegalArgumentException("计费方案与厂商接口不匹配");
        }
        String purpose = StringUtils.hasText(request.getAccountingPurpose())
                ? request.getAccountingPurpose() : "VENDOR_PAYABLE";
        if (!purpose.equals(plan.getAccountingPurpose())) {
            throw new IllegalArgumentException("计费方案会计方向不匹配");
        }
        LocalDateTime at = request.getCallTime() != null ? request.getCallTime() : LocalDateTime.now();
        if (at.isBefore(plan.getEffectiveFrom())
                || (plan.getEffectiveTo() != null && !at.isBefore(plan.getEffectiveTo()))) {
            throw new IllegalArgumentException("计费方案在调用时间未生效");
        }
        String policyHash = codec.sha256(plan.getMeteringConfig());
        if (!policyHash.equals(request.getPolicyHash())) {
            throw new IllegalArgumentException("计量策略已变化，请重新解析策略后再计费");
        }
    }

    private String scopeKey(BillingPlanModel model, BillingChargeReqDTO request) {
        String scope = model.getMetering() != null
                ? model.getMetering().getAggregationScope() : null;
        return switch (scope == null ? "VENDOR_INTERFACE" : scope) {
            case "TENANT" -> "TENANT:" + requiredId(request.getTenantId(), "tenantId");
            case "CALLER" -> "CALLER:" + requiredId(request.getCallerId(), "callerId");
            default -> "VENDOR_INTERFACE";
        };
    }

    private Long requiredId(Long id, String name) {
        if (id == null) throw new IllegalArgumentException(name + "是当前累计范围的必填字段");
        return id;
    }

    private BillingEvent buildEvent(BillingPlan plan, BillingPlanModel model,
                                    BillingChargeReqDTO request,
                                    BillingMeteringEvaluator.Evaluation evaluation,
                                    BillingPricingEngine.PricingResult pricing,
                                    BigDecimal usageBefore, LocalDate period,
                                    LocalDateTime callTime) {
        BillingEvent event = new BillingEvent();
        event.setRequestId(request.getRequestId());
        event.setEventType("USAGE");
        event.setPlanId(plan.getId());
        event.setPlanCode(plan.getPlanCode());
        event.setPlanVersion(plan.getVersion());
        event.setTemplateCode(plan.getTemplateCode());
        event.setAccountingPurpose(plan.getAccountingPurpose());
        event.setTenantId(request.getTenantId());
        event.setCallerId(request.getCallerId());
        event.setVendorId(plan.getVendorId());
        event.setVendorCode(plan.getVendorCode());
        event.setInterfaceId(plan.getInterfaceId());
        event.setInterfaceCode(plan.getInterfaceCode());
        event.setDataType(request.getDataType());
        event.setBillable(evaluation.billable());
        event.setQuantity(pricing.quantity());
        event.setUnit(model.getMetering().getQuantity().getUnit());
        event.setUsageBefore(usageBefore);
        event.setBaseAmount(pricing.baseAmount());
        event.setAdjustmentAmount(pricing.adjustmentAmount());
        event.setFinalAmount(pricing.finalAmount());
        event.setCurrency(plan.getCurrency());
        event.setStatus(evaluation.pendingReview() ? "PENDING_REVIEW" : "POSTED");
        event.setEvidenceHash(codec.sha256(request.getMeteringFacts()));
        event.setDecisionDetail(codec.write(evaluation.decisions()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pricing", model.getPricing());
        snapshot.put("adjustment", model.getAdjustment());
        snapshot.put("tiers", model.getTiers());
        event.setPricingSnapshot(codec.write(snapshot));
        event.setBillingPeriod(period);
        event.setCallTime(callTime);
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }

    private void aggregateDaily(BillingEvent event, BillingChargeReqDTO request) {
        if (!"VENDOR_PAYABLE".equals(event.getAccountingPurpose())) return;
        if (event.getTenantId() == null || event.getCallerId() == null || event.getVendorId() == null) return;
        dailyMapper.upsertDailyFromCallRecord(
                event.getRequestId(), event.getTenantId(), event.getCallerId(), event.getVendorId(),
                StringUtils.hasText(event.getDataType()) ? event.getDataType() : "unknown",
                event.getCallTime().toLocalDate(),
                Boolean.TRUE.equals(request.getSuccess()) ? 1L : 0L,
                Boolean.TRUE.equals(request.getSuccess()) ? 0L : 1L,
                event.getFinalAmount(),
                request.getLatencyMs() != null ? request.getLatencyMs().intValue() : null);
    }

    private BillingChargeRespDTO toResponse(BillingEvent event) {
        BillingChargeRespDTO response = new BillingChargeRespDTO();
        response.setBillingEventId(event.getId());
        response.setPlanId(event.getPlanId());
        response.setPlanCode(event.getPlanCode());
        response.setPlanVersion(event.getPlanVersion());
        response.setTemplateCode(event.getTemplateCode());
        response.setBillable(event.getBillable());
        response.setQuantity(event.getQuantity());
        response.setUnit(event.getUnit());
        response.setBaseAmount(event.getBaseAmount());
        response.setAdjustmentAmount(event.getAdjustmentAmount());
        response.setFinalAmount(event.getFinalAmount());
        response.setCurrency(event.getCurrency());
        response.setStatus(event.getStatus());
        response.setDecisionDetail(event.getDecisionDetail());
        return response;
    }
}
