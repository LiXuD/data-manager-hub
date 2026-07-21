package com.dataplatform.billing.service;

import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.entity.BillingPlanTier;
import com.dataplatform.billing.model.BillingPlanModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** 计费配置的唯一序列化入口，保证策略哈希和事件快照稳定。 */
@Component
public class BillingConfigCodec {

    private final ObjectMapper objectMapper;

    public BillingConfigCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("计费配置无法序列化", exception);
        }
    }

    public <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("计费配置损坏: " + type.getSimpleName(), exception);
        }
    }

    public String sha256(Object value) {
        return sha256(write(value));
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM缺少SHA-256算法", exception);
        }
    }

    public BillingPlanModel toModel(BillingPlan entity, List<BillingPlanTier> tiers) {
        BillingPlanModel model = new BillingPlanModel();
        model.setId(entity.getId());
        model.setPlanCode(entity.getPlanCode());
        model.setVersion(entity.getVersion());
        model.setPlanName(entity.getPlanName());
        model.setVendorId(entity.getVendorId());
        model.setVendorCode(entity.getVendorCode());
        model.setVendorName(entity.getVendorName());
        model.setInterfaceId(entity.getInterfaceId());
        model.setInterfaceCode(entity.getInterfaceCode());
        model.setInterfaceName(entity.getInterfaceName());
        model.setTemplateCode(entity.getTemplateCode());
        model.setAccountingPurpose(entity.getAccountingPurpose());
        model.setCurrency(entity.getCurrency());
        model.setTimezone(entity.getTimezone());
        model.setSettlementCycle(entity.getSettlementCycle());
        model.setStatus(entity.getStatus());
        model.setEffectiveFrom(entity.getEffectiveFrom());
        model.setEffectiveTo(entity.getEffectiveTo());
        model.setContractFingerprint(entity.getContractFingerprint());
        model.setPricing(read(entity.getPricingConfig(), BillingPlanModel.PricingConfig.class,
                new BillingPlanModel.PricingConfig()));
        model.setMetering(read(entity.getMeteringConfig(), BillingPlanModel.MeteringConfig.class,
                new BillingPlanModel.MeteringConfig()));
        model.setAdjustment(read(entity.getAdjustmentConfig(), BillingPlanModel.AdjustmentConfig.class,
                new BillingPlanModel.AdjustmentConfig()));
        model.setTiers(tiers.stream().map(this::toTierModel).toList());
        return model;
    }

    private BillingPlanModel.TierConfig toTierModel(BillingPlanTier entity) {
        BillingPlanModel.TierConfig tier = new BillingPlanModel.TierConfig();
        tier.setId(entity.getId());
        tier.setTierMin(entity.getTierMin());
        tier.setTierMax(entity.getTierMax());
        tier.setUnitPrice(entity.getUnitPrice());
        tier.setDiscount(entity.getDiscount());
        tier.setSortOrder(entity.getSortOrder());
        return tier;
    }
}
