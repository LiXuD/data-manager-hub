package com.dataplatform.billing.service;

import com.dataplatform.billing.entity.BillingEvent;
import com.dataplatform.billing.mapper.BillingEventMapper;
import com.dataplatform.billing.mapper.BillingUsageBalanceMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.billing.model.BillingReversalCommand;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 以追加负向事件实现全额冲正，原始账本事实保持不可变。 */
@Service
public class BillingReversalService {

    private final BillingEventMapper eventMapper;
    private final BillingUsageBalanceMapper usageMapper;
    private final BillingPlanService planService;
    private final BillingConfigCodec codec;

    public BillingReversalService(BillingEventMapper eventMapper,
                                  BillingUsageBalanceMapper usageMapper,
                                  BillingPlanService planService,
                                  BillingConfigCodec codec) {
        this.eventMapper = eventMapper;
        this.usageMapper = usageMapper;
        this.planService = planService;
        this.codec = codec;
    }

    @Transactional
    public BillingEvent reverse(Long eventId, BillingReversalCommand command) {
        if (command == null || !StringUtils.hasText(command.getRequestId())) {
            throw new IllegalArgumentException("冲正requestId不能为空");
        }
        if (!StringUtils.hasText(command.getReason())) {
            throw new IllegalArgumentException("冲正原因不能为空");
        }
        eventMapper.lockRequest(command.getRequestId());
        BillingEvent byRequest = eventMapper.selectByRequestId(command.getRequestId());
        if (byRequest != null) {
            if (!"REVERSAL".equals(byRequest.getEventType())
                    || !eventId.equals(byRequest.getOriginalEventId())) {
                throw new IllegalArgumentException("冲正requestId已被其他事件使用");
            }
            return byRequest;
        }
        BillingEvent original = eventMapper.selectById(eventId);
        if (original == null) throw new IllegalArgumentException("原始计费事件不存在");
        if ("REVERSAL".equals(original.getEventType())) {
            throw new IllegalArgumentException("冲正事件不可再次冲正");
        }
        BillingEvent existing = eventMapper.selectReversalByOriginalEventId(eventId);
        if (existing != null) return existing;

        restoreUsage(original);
        BillingEvent reversal = copyNegative(original, command);
        eventMapper.insert(reversal);
        return reversal;
    }

    private void restoreUsage(BillingEvent original) {
        if (!"USAGE".equals(original.getEventType()) || !Boolean.TRUE.equals(original.getBillable())
                || original.getQuantity() == null || original.getQuantity().signum() <= 0) return;
        BillingPlanModel plan = planService.get(original.getPlanId());
        String aggregation = plan.getMetering() != null
                ? plan.getMetering().getAggregationScope() : null;
        String scopeKey = switch (aggregation == null ? "VENDOR_INTERFACE" : aggregation) {
            case "TENANT" -> "TENANT:" + requiredId(original.getTenantId(), "tenantId");
            case "CALLER" -> "CALLER:" + requiredId(original.getCallerId(), "callerId");
            default -> "VENDOR_INTERFACE";
        };
        String lockKey = original.getPlanId() + ":" + original.getBillingPeriod() + ":" + scopeKey;
        usageMapper.lockBalance(lockKey);
        usageMapper.decrement(original.getPlanId(), original.getBillingPeriod(), scopeKey,
                original.getQuantity());
    }

    private BillingEvent copyNegative(BillingEvent source, BillingReversalCommand command) {
        BillingEvent target = new BillingEvent();
        target.setRequestId(command.getRequestId());
        target.setEventType("REVERSAL");
        target.setOriginalEventId(source.getId());
        target.setPlanId(source.getPlanId());
        target.setPlanCode(source.getPlanCode());
        target.setPlanVersion(source.getPlanVersion());
        target.setTemplateCode(source.getTemplateCode());
        target.setAccountingPurpose(source.getAccountingPurpose());
        target.setTenantId(source.getTenantId());
        target.setCallerId(source.getCallerId());
        target.setVendorId(source.getVendorId());
        target.setVendorCode(source.getVendorCode());
        target.setInterfaceId(source.getInterfaceId());
        target.setInterfaceCode(source.getInterfaceCode());
        target.setDataType(source.getDataType());
        target.setBillable(false);
        target.setQuantity(negative(source.getQuantity()));
        target.setUnit(source.getUnit());
        target.setUsageBefore(source.getUsageBefore());
        target.setBaseAmount(negative(source.getBaseAmount()));
        target.setAdjustmentAmount(negative(source.getAdjustmentAmount()));
        target.setFinalAmount(negative(source.getFinalAmount()));
        target.setCurrency(source.getCurrency());
        target.setStatus("POSTED");
        target.setEvidenceHash(codec.sha256(command.getReason()));
        target.setDecisionDetail(codec.write(java.util.Map.of(
                "reason", command.getReason(), "originalEventId", source.getId())));
        target.setPricingSnapshot(source.getPricingSnapshot());
        target.setBillingPeriod(source.getBillingPeriod());
        target.setCallTime(LocalDateTime.now());
        target.setCreatedAt(LocalDateTime.now());
        return target;
    }

    private BigDecimal negative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.negate();
    }

    private Long requiredId(Long id, String name) {
        if (id == null) throw new IllegalArgumentException("原事件缺少" + name + "，无法恢复累计用量");
        return id;
    }
}
