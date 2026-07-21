package com.dataplatform.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.api.Result;
import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import com.dataplatform.billing.api.dto.BillingAdditionalPlanDTO;
import com.dataplatform.billing.entity.BillingPlan;
import com.dataplatform.billing.entity.BillingPlanTier;
import com.dataplatform.billing.entity.BillingTemplate;
import com.dataplatform.billing.mapper.BillingPlanMapper;
import com.dataplatform.billing.mapper.BillingPlanTierMapper;
import com.dataplatform.billing.mapper.BillingTemplateMapper;
import com.dataplatform.billing.model.BillingPlanModel;
import com.dataplatform.masterdata.interface_.api.dto.ApiInterfaceDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.feign.ApiInterfaceFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 版本化计费方案的创建、校验、发布和运行时解析。 */
@Service
public class BillingPlanService {

    private final BillingPlanMapper planMapper;
    private final BillingPlanTierMapper tierMapper;
    private final BillingTemplateMapper templateMapper;
    private final VendorInternalFeignClient vendorClient;
    private final ApiInterfaceFeignClient interfaceClient;
    private final BillingConfigCodec codec;
    private final BillingPlanValidator validator;

    public BillingPlanService(BillingPlanMapper planMapper,
                              BillingPlanTierMapper tierMapper,
                              BillingTemplateMapper templateMapper,
                              VendorInternalFeignClient vendorClient,
                              ApiInterfaceFeignClient interfaceClient,
                              BillingConfigCodec codec,
                              BillingPlanValidator validator) {
        this.planMapper = planMapper;
        this.tierMapper = tierMapper;
        this.templateMapper = templateMapper;
        this.vendorClient = vendorClient;
        this.interfaceClient = interfaceClient;
        this.codec = codec;
        this.validator = validator;
    }

    public List<BillingTemplate> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<BillingTemplate>()
                .eq(BillingTemplate::getStatus, "ACTIVE")
                .orderByAsc(BillingTemplate::getId));
    }

    public List<BillingPlanModel> listPlans() {
        return planMapper.selectList(new LambdaQueryWrapper<BillingPlan>()
                        .orderByDesc(BillingPlan::getUpdatedAt))
                .stream().map(this::toModel).toList();
    }

    public BillingPlanModel get(Long id) {
        BillingPlan entity = requirePlan(id);
        return toModel(entity);
    }

    public BillingPlan getEntity(Long id) {
        return requirePlan(id);
    }

    public BillingPlanModel getEffective(String vendorCode, String interfaceCode, LocalDateTime callTime) {
        BillingPlan plan = planMapper.selectEffective(vendorCode, interfaceCode,
                "VENDOR_PAYABLE", callTime != null ? callTime : LocalDateTime.now());
        return plan != null ? toModel(plan) : null;
    }

    @Transactional
    public BillingPlanModel createDraft(BillingPlanModel command) {
        command.setId(null);
        command.setPlanCode(command.getPlanCode() == null || command.getPlanCode().isBlank()
                ? "PLAN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT)
                : command.getPlanCode());
        command.setVersion(planMapper.selectMaxVersion(command.getPlanCode()) + 1);
        command.setStatus("DRAFT");
        if (command.getEffectiveFrom() == null) command.setEffectiveFrom(LocalDateTime.now());
        Enrichment enrichment = enrich(command);
        List<String> errors = validator.validate(command, enrichment.contract());
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("；", errors));
        BillingPlan entity = toEntity(command);
        planMapper.insert(entity);
        replaceTiers(entity.getId(), command.getTiers());
        return get(entity.getId());
    }

    @Transactional
    public BillingPlanModel updateDraft(Long id, BillingPlanModel command) {
        BillingPlan existing = requirePlan(id);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new IllegalArgumentException("已发布方案不可直接修改，请创建新版本");
        }
        command.setId(id);
        command.setPlanCode(existing.getPlanCode());
        command.setVersion(existing.getVersion());
        command.setStatus("DRAFT");
        Enrichment enrichment = enrich(command);
        List<String> errors = validator.validate(command, enrichment.contract());
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("；", errors));
        BillingPlan entity = toEntity(command);
        entity.setCreatedAt(existing.getCreatedAt());
        entity.setCreatedBy(existing.getCreatedBy());
        planMapper.updateById(entity);
        replaceTiers(id, command.getTiers());
        return get(id);
    }

    @Transactional
    public BillingPlanModel createNextVersion(Long id) {
        BillingPlanModel previous = get(id);
        previous.setId(null);
        previous.setVersion(planMapper.selectMaxVersion(previous.getPlanCode()) + 1);
        previous.setStatus("DRAFT");
        previous.setEffectiveFrom(LocalDateTime.now());
        previous.setEffectiveTo(null);
        BillingPlan entity = toEntity(previous);
        planMapper.insert(entity);
        replaceTiers(entity.getId(), previous.getTiers());
        return get(entity.getId());
    }

    public List<String> validate(Long id) {
        BillingPlanModel plan = get(id);
        InterfaceContractDTO contract = requireData(interfaceClient.getContract(plan.getInterfaceId()), "接口响应契约不存在");
        return validator.validate(plan, contract);
    }

    @Transactional
    public BillingPlanModel publish(Long id) {
        BillingPlan existing = requirePlan(id);
        if (!"DRAFT".equals(existing.getStatus()) && !"NEEDS_REVIEW".equals(existing.getStatus())) {
            throw new IllegalArgumentException("只有草稿或待复核方案可以发布");
        }
        BillingPlanModel plan = toModel(existing);
        InterfaceContractDTO contract = requireData(interfaceClient.getContract(plan.getInterfaceId()), "接口响应契约不存在");
        List<String> errors = validator.validate(plan, contract);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("；", errors));
        closeSupersededVersion(plan);
        rejectOverlappingDifferentPlan(plan);
        existing.setContractFingerprint(codec.sha256(contract.getResponseFields()));
        existing.setPublishedAt(LocalDateTime.now());
        existing.setStatus(!plan.getEffectiveFrom().isAfter(LocalDateTime.now()) ? "ACTIVE" : "PUBLISHED");
        existing.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(existing);
        return get(id);
    }

    @Transactional
    public void deleteDraft(Long id) {
        BillingPlan existing = requirePlan(id);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new IllegalArgumentException("只能删除草稿方案");
        }
        planMapper.deleteById(id);
    }

    public BillingMeteringPolicyDTO resolvePolicy(String vendorCode, String interfaceCode,
                                                  LocalDateTime callTime) {
        BillingPlan plan = planMapper.selectEffective(vendorCode, interfaceCode,
                "VENDOR_PAYABLE", callTime != null ? callTime : LocalDateTime.now());
        if (plan == null) throw new IllegalStateException("没有匹配的已发布计费方案");
        BillingPlanModel model = toModel(plan);
        BillingMeteringPolicyDTO dto = new BillingMeteringPolicyDTO();
        dto.setPlanId(plan.getId());
        dto.setPlanCode(plan.getPlanCode());
        dto.setPlanVersion(plan.getVersion());
        dto.setTemplateCode(plan.getTemplateCode());
        dto.setPolicyHash(codec.sha256(plan.getMeteringConfig()));
        dto.setEffectiveFrom(plan.getEffectiveFrom());
        dto.setEffectiveTo(plan.getEffectiveTo());
        dto.setSelectors(buildSelectors(model));
        BillingPlan chargeback = planMapper.selectEffective(vendorCode, interfaceCode,
                "INTERNAL_CHARGEBACK", callTime != null ? callTime : LocalDateTime.now());
        if (chargeback != null) {
            BillingPlanModel chargebackModel = toModel(chargeback);
            BillingAdditionalPlanDTO additional = new BillingAdditionalPlanDTO();
            additional.setPlanId(chargeback.getId());
            additional.setPlanCode(chargeback.getPlanCode());
            additional.setPlanVersion(chargeback.getVersion());
            additional.setTemplateCode(chargeback.getTemplateCode());
            additional.setAccountingPurpose(chargeback.getAccountingPurpose());
            additional.setPolicyHash(codec.sha256(chargeback.getMeteringConfig()));
            additional.setSelectors(buildSelectors(chargebackModel));
            dto.setAdditionalPlans(List.of(additional));
        }
        return dto;
    }

    public List<BillingPlanTier> tiers(Long planId) {
        return tierMapper.selectList(new LambdaQueryWrapper<BillingPlanTier>()
                .eq(BillingPlanTier::getPlanId, planId)
                .orderByAsc(BillingPlanTier::getSortOrder, BillingPlanTier::getTierMin));
    }

    private List<BillingMeteringPolicyDTO.SelectorDTO> buildSelectors(BillingPlanModel plan) {
        List<BillingMeteringPolicyDTO.SelectorDTO> result = new ArrayList<>();
        List<BillingPlanModel.ConditionConfig> conditions = plan.getMetering().getConditions() != null
                ? plan.getMetering().getConditions() : List.of();
        for (int index = 0; index < conditions.size(); index++) {
            BillingPlanModel.ConditionConfig condition = conditions.get(index);
            if ("METADATA".equalsIgnoreCase(condition.getSource())) continue;
            result.add(selector(condition.getAlias() == null || condition.getAlias().isBlank()
                            ? "condition-" + index : condition.getAlias(), condition.getSource(),
                    condition.getFieldId(), condition.getPath(), condition.getExtraction()));
        }
        BillingPlanModel.QuantityConfig quantity = plan.getMetering().getQuantity();
        if (quantity != null && !"FIXED".equalsIgnoreCase(quantity.getType())
                && !"DURATION".equalsIgnoreCase(quantity.getType())
                && !"METADATA".equalsIgnoreCase(quantity.getSource())) {
            result.add(selector(quantity.getAlias(), quantity.getSource(), quantity.getFieldId(),
                    quantity.getPath(), quantity.getExtraction()));
        }
        return result;
    }

    private BillingMeteringPolicyDTO.SelectorDTO selector(String alias, String source, Long fieldId,
                                                          String path, String extraction) {
        BillingMeteringPolicyDTO.SelectorDTO dto = new BillingMeteringPolicyDTO.SelectorDTO();
        dto.setAlias(alias);
        dto.setSource(source);
        dto.setFieldId(fieldId);
        dto.setPath(path);
        dto.setExtraction(extraction);
        return dto;
    }

    private Enrichment enrich(BillingPlanModel command) {
        BillingTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<BillingTemplate>()
                .eq(BillingTemplate::getTemplateCode, command.getTemplateCode() == null
                        ? null : command.getTemplateCode().toUpperCase(Locale.ROOT))
                .eq(BillingTemplate::getStatus, "ACTIVE")
                .orderByDesc(BillingTemplate::getTemplateVersion)
                .last("LIMIT 1"));
        if (template == null) throw new IllegalArgumentException("计费模板不存在或未启用");
        VendorInfoDTO vendor = requireData(vendorClient.getById(command.getVendorId()), "厂商不存在");
        ApiInterfaceDTO apiInterface = requireData(interfaceClient.getById(command.getInterfaceId()), "接口不存在");
        if (!command.getVendorId().equals(apiInterface.getVendorId())) {
            throw new IllegalArgumentException("所选接口不属于指定厂商");
        }
        InterfaceContractDTO contract = requireData(interfaceClient.getContract(command.getInterfaceId()),
                "接口响应契约不存在");
        command.setVendorCode(vendor.getVendorCode());
        command.setVendorName(vendor.getVendorName());
        command.setInterfaceCode(apiInterface.getInterfaceCode());
        command.setInterfaceName(apiInterface.getInterfaceName());
        command.setContractFingerprint(codec.sha256(contract.getResponseFields()));
        return new Enrichment(vendor, apiInterface, contract);
    }

    private <T> T requireData(Result<T> result, String message) {
        if (result == null || result.getData() == null) throw new IllegalArgumentException(message);
        return result.getData();
    }

    private BillingPlan requirePlan(Long id) {
        BillingPlan plan = planMapper.selectById(id);
        if (plan == null) throw new IllegalArgumentException("计费方案不存在: " + id);
        return plan;
    }

    private BillingPlanModel toModel(BillingPlan entity) {
        return codec.toModel(entity, tiers(entity.getId()));
    }

    private BillingPlan toEntity(BillingPlanModel model) {
        BillingPlan entity = new BillingPlan();
        entity.setId(model.getId());
        entity.setPlanCode(model.getPlanCode());
        entity.setVersion(model.getVersion());
        entity.setPlanName(model.getPlanName());
        entity.setVendorId(model.getVendorId());
        entity.setVendorCode(model.getVendorCode());
        entity.setVendorName(model.getVendorName());
        entity.setInterfaceId(model.getInterfaceId());
        entity.setInterfaceCode(model.getInterfaceCode());
        entity.setInterfaceName(model.getInterfaceName());
        entity.setTemplateCode(model.getTemplateCode().toUpperCase(Locale.ROOT));
        entity.setAccountingPurpose(defaultValue(model.getAccountingPurpose(), "VENDOR_PAYABLE"));
        entity.setCurrency(defaultValue(model.getCurrency(), "CNY"));
        entity.setTimezone(defaultValue(model.getTimezone(), "Asia/Shanghai"));
        entity.setSettlementCycle(defaultValue(model.getSettlementCycle(), "MONTH"));
        entity.setPricingConfig(codec.write(model.getPricing()));
        entity.setMeteringConfig(codec.write(model.getMetering()));
        entity.setAdjustmentConfig(codec.write(model.getAdjustment()));
        entity.setContractFingerprint(model.getContractFingerprint());
        entity.setStatus(defaultValue(model.getStatus(), "DRAFT"));
        entity.setEffectiveFrom(model.getEffectiveFrom());
        entity.setEffectiveTo(model.getEffectiveTo());
        entity.setUpdatedAt(LocalDateTime.now());
        if (model.getId() == null) entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private void replaceTiers(Long planId, List<BillingPlanModel.TierConfig> tiers) {
        tierMapper.delete(new LambdaQueryWrapper<BillingPlanTier>().eq(BillingPlanTier::getPlanId, planId));
        if (tiers == null) return;
        for (int index = 0; index < tiers.size(); index++) {
            BillingPlanModel.TierConfig source = tiers.get(index);
            BillingPlanTier tier = new BillingPlanTier();
            tier.setPlanId(planId);
            tier.setTierMin(source.getTierMin());
            tier.setTierMax(source.getTierMax());
            tier.setUnitPrice(source.getUnitPrice());
            tier.setDiscount(source.getDiscount());
            tier.setSortOrder(index);
            tierMapper.insert(tier);
        }
    }

    private void closeSupersededVersion(BillingPlanModel candidate) {
        List<BillingPlan> versions = planMapper.selectList(new LambdaQueryWrapper<BillingPlan>()
                .eq(BillingPlan::getPlanCode, candidate.getPlanCode())
                .ne(BillingPlan::getId, candidate.getId())
                .in(BillingPlan::getStatus, "PUBLISHED", "ACTIVE", "NEEDS_REVIEW")
                .orderByDesc(BillingPlan::getVersion));
        for (BillingPlan version : versions) {
            if (overlaps(version.getEffectiveFrom(), version.getEffectiveTo(),
                    candidate.getEffectiveFrom(), candidate.getEffectiveTo())) {
                if (!candidate.getEffectiveFrom().isAfter(version.getEffectiveFrom())) {
                    throw new IllegalArgumentException("新版本生效时间必须晚于当前版本生效时间");
                }
                version.setEffectiveTo(candidate.getEffectiveFrom());
                if (!candidate.getEffectiveFrom().isAfter(LocalDateTime.now())) version.setStatus("EXPIRED");
                version.setUpdatedAt(LocalDateTime.now());
                planMapper.updateById(version);
            }
        }
    }

    private void rejectOverlappingDifferentPlan(BillingPlanModel candidate) {
        List<BillingPlan> plans = planMapper.selectList(new LambdaQueryWrapper<BillingPlan>()
                .eq(BillingPlan::getVendorId, candidate.getVendorId())
                .eq(BillingPlan::getInterfaceId, candidate.getInterfaceId())
                .eq(BillingPlan::getAccountingPurpose, candidate.getAccountingPurpose())
                .ne(BillingPlan::getId, candidate.getId())
                .ne(BillingPlan::getPlanCode, candidate.getPlanCode())
                .in(BillingPlan::getStatus, "PUBLISHED", "ACTIVE", "NEEDS_REVIEW"));
        boolean conflict = plans.stream().anyMatch(item -> overlaps(item.getEffectiveFrom(), item.getEffectiveTo(),
                candidate.getEffectiveFrom(), candidate.getEffectiveTo()));
        if (conflict) throw new IllegalArgumentException("同一厂商、接口和计费方向存在生效区间重叠的方案");
    }

    private boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd,
                             LocalDateTime rightStart, LocalDateTime rightEnd) {
        return (leftEnd == null || rightStart.isBefore(leftEnd))
                && (rightEnd == null || leftStart.isBefore(rightEnd));
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Enrichment(VendorInfoDTO vendor, ApiInterfaceDTO apiInterface,
                              InterfaceContractDTO contract) {
    }
}
