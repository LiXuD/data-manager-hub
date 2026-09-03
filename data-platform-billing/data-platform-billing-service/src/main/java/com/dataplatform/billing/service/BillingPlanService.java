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
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.feign.VendorConfigInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.feign.VendorInternalFeignClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 版本化计费方案的创建、校验、发布和运行时解析。 */
@Service
public class BillingPlanService {

    private final BillingPlanMapper planMapper;
    private final BillingPlanTierMapper tierMapper;
    private final BillingTemplateMapper templateMapper;
    private final VendorInternalFeignClient vendorClient;
    private final VendorConfigInternalFeignClient vendorConfigClient;
    private final ApiInterfaceFeignClient interfaceClient;
    private final BillingConfigCodec codec;
    private final BillingPlanValidator validator;

    public BillingPlanService(BillingPlanMapper planMapper,
                              BillingPlanTierMapper tierMapper,
                              BillingTemplateMapper templateMapper,
                              VendorInternalFeignClient vendorClient,
                              VendorConfigInternalFeignClient vendorConfigClient,
                              ApiInterfaceFeignClient interfaceClient,
                              BillingConfigCodec codec,
                              BillingPlanValidator validator) {
        this.planMapper = planMapper;
        this.tierMapper = tierMapper;
        this.templateMapper = templateMapper;
        this.vendorClient = vendorClient;
        this.vendorConfigClient = vendorConfigClient;
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
        BillingPlan plan = resolveEffectivePlan(vendorCode, interfaceCode, "VENDOR_PAYABLE",
                callTime != null ? callTime : LocalDateTime.now());
        return plan != null ? toModel(plan) : null;
    }

    @Transactional
    public BillingPlanModel createDraft(BillingPlanModel command) {
        if (command == null) {
            throw BillingPlanException.badRequest("计费方案不能为空");
        }
        validateEnrichmentInputs(command);
        command.setId(null);
        command.setPlanCode(command.getPlanCode() == null || command.getPlanCode().isBlank()
                ? "PLAN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT)
                : command.getPlanCode().trim());
        command.setVersion(safeMaxVersion(command.getPlanCode()) + 1);
        command.setStatus("DRAFT");
        if (command.getEffectiveFrom() == null) command.setEffectiveFrom(LocalDateTime.now());
        Enrichment enrichment = enrich(command);
        List<String> errors = safeErrors(validator.validate(command, enrichment.contract()));
        if (!errors.isEmpty()) throw BillingPlanException.validation(errors);
        BillingPlan entity = toEntity(command);
        if (planMapper.insert(entity) <= 0 || entity.getId() == null) {
            throw BillingPlanException.conflict("BILLING_PLAN_PERSIST_FAILED", "计费方案保存失败");
        }
        replaceTiers(entity.getId(), command.getTiers());
        return get(entity.getId());
    }

    @Transactional
    public BillingPlanModel updateDraft(Long id, BillingPlanModel command) {
        if (command == null) {
            throw BillingPlanException.badRequest("计费方案不能为空");
        }
        BillingPlan existing = requirePlan(id);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw BillingPlanException.conflict("BILLING_PLAN_IMMUTABLE",
                    "已发布方案不可直接修改，请创建新版本");
        }
        command.setId(id);
        command.setPlanCode(existing.getPlanCode());
        command.setVersion(existing.getVersion());
        command.setStatus("DRAFT");
        validateEnrichmentInputs(command);
        Enrichment enrichment = enrich(command);
        List<String> errors = safeErrors(validator.validate(command, enrichment.contract()));
        if (!errors.isEmpty()) throw BillingPlanException.validation(errors);
        BillingPlan entity = toEntity(command);
        entity.setCreatedAt(existing.getCreatedAt());
        entity.setCreatedBy(existing.getCreatedBy());
        if (planMapper.updateById(entity) <= 0) {
            throw BillingPlanException.conflict("BILLING_PLAN_PERSIST_FAILED", "计费方案更新失败");
        }
        replaceTiers(id, command.getTiers());
        return get(id);
    }

    @Transactional
    public BillingPlanModel createNextVersion(Long id) {
        BillingPlanModel previous = get(id);
        previous.setId(null);
        previous.setVersion(safeMaxVersion(previous.getPlanCode()) + 1);
        previous.setStatus("DRAFT");
        previous.setEffectiveFrom(LocalDateTime.now());
        previous.setEffectiveTo(null);
        BillingPlan entity = toEntity(previous);
        if (planMapper.insert(entity) <= 0 || entity.getId() == null) {
            throw BillingPlanException.conflict("BILLING_PLAN_PERSIST_FAILED", "计费方案新版本保存失败");
        }
        replaceTiers(entity.getId(), previous.getTiers());
        return get(entity.getId());
    }

    public List<String> validate(Long id) {
        BillingPlan entity = requirePlan(id);
        BillingPlanModel plan = toModel(entity);
        List<String> errors = new ArrayList<>();
        if (!"DRAFT".equals(entity.getStatus()) && !"NEEDS_REVIEW".equals(entity.getStatus())) {
            errors.add("只有草稿或待复核方案可以发布");
        }
        errors.addAll(publishKeyErrors(entity));
        if (entity.getInterfaceId() == null || entity.getVendorId() == null) {
            return errors;
        }
        InterfaceContractDTO contract = requireData(safeCall(
                () -> interfaceClient.getContract(entity.getInterfaceId()), "接口服务暂不可用"),
                "接口响应契约不存在");
        errors.addAll(safeErrors(validator.validate(plan, contract)));
        if (!errors.isEmpty() || entity.getVendorId() == null) {
            return errors;
        }
        List<BillingPlan> publishablePlans = planMapper.selectPublishable(
                entity.getVendorId(), entity.getInterfaceId(),
                defaultValue(entity.getAccountingPurpose(), "VENDOR_PAYABLE"));
        PublishConflict conflict = findPublishConflict(plan, publishablePlans);
        if (conflict != null) {
            errors.add(conflict.message());
        }
        return errors;
    }

    @Transactional
    public BillingPlanModel publish(Long id) {
        BillingPlan existing = requirePlanForUpdate(id);
        if (!"DRAFT".equals(existing.getStatus()) && !"NEEDS_REVIEW".equals(existing.getStatus())) {
            throw BillingPlanException.conflict("BILLING_PLAN_STATE_CONFLICT",
                    "只有草稿或待复核方案可以发布");
        }
        requirePublishKey(existing);
        planMapper.ensurePublishLock(existing.getVendorId(), existing.getInterfaceId(),
                defaultValue(existing.getAccountingPurpose(), "VENDOR_PAYABLE"));
        Long publishLock = planMapper.lockPublishKey(existing.getVendorId(), existing.getInterfaceId(),
                defaultValue(existing.getAccountingPurpose(), "VENDOR_PAYABLE"));
        if (publishLock == null) {
            throw BillingPlanException.conflict("BILLING_PLAN_LOCK_UNAVAILABLE", "计费方案发布锁不可用");
        }

        // The candidate row is already locked. Re-read the business-key rows only
        // after acquiring the durable lock, then validate and inspect conflicts in
        // one serialized transaction.
        BillingPlanModel plan = toModel(existing);
        InterfaceContractDTO contract = requireData(safeCall(
                () -> interfaceClient.getContract(plan.getInterfaceId()), "接口服务暂不可用"),
                "接口响应契约不存在");
        List<String> errors = safeErrors(validator.validate(plan, contract));
        if (!errors.isEmpty()) throw BillingPlanException.validation(errors);
        List<BillingPlan> publishablePlans = planMapper.selectPublishableForUpdate(
                plan.getVendorId(), plan.getInterfaceId(),
                defaultValue(plan.getAccountingPurpose(), "VENDOR_PAYABLE"));
        rejectPublishConflicts(plan, publishablePlans);
        closeSupersededVersion(plan, publishablePlans);
        LocalDateTime publishedAt = LocalDateTime.now();
        existing.setContractFingerprint(codec.sha256(contract.getResponseFields()));
        existing.setPublishedAt(publishedAt);
        existing.setStatus(!plan.getEffectiveFrom().isAfter(publishedAt) ? "ACTIVE" : "PUBLISHED");
        existing.setUpdatedAt(publishedAt);
        if (planMapper.updateById(existing) <= 0) {
            throw BillingPlanException.conflict("BILLING_PLAN_PERSIST_FAILED", "计费方案发布失败");
        }
        return get(id);
    }

    @Transactional
    public void deleteDraft(Long id) {
        BillingPlan existing = requirePlan(id);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw BillingPlanException.conflict("BILLING_PLAN_IMMUTABLE", "只能删除草稿方案");
        }
        if (planMapper.deleteById(id) <= 0) {
            throw BillingPlanException.conflict("BILLING_PLAN_PERSIST_FAILED", "计费方案删除失败");
        }
    }

    public BillingMeteringPolicyDTO resolvePolicy(String vendorCode, String interfaceCode,
                                                  LocalDateTime callTime) {
        LocalDateTime effectiveAt = callTime != null ? callTime : LocalDateTime.now();
        BillingPlan plan = resolveEffectivePlan(vendorCode, interfaceCode, "VENDOR_PAYABLE", effectiveAt);
        if (plan == null) throw BillingPlanException.notFound("没有匹配的已发布计费方案");
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
        BillingPlan chargeback = resolveEffectivePlan(vendorCode, interfaceCode,
                "INTERNAL_CHARGEBACK", effectiveAt);
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

    private BillingPlan resolveEffectivePlan(String vendorCode, String interfaceCode,
                                             String accountingPurpose, LocalDateTime callTime) {
        List<BillingPlan> candidates = planMapper.selectEffective(
                vendorCode, interfaceCode, accountingPurpose, callTime);
        if (candidates == null) {
            return null;
        }
        if (candidates.size() > 1) {
            throw BillingPlanException.conflict("BILLING_PLAN_DATA_CONFLICT",
                    "计费方案数据冲突：同一厂商、接口、计费方向和调用时间匹配到多个生效版本");
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    public List<BillingPlanTier> tiers(Long planId) {
        List<BillingPlanTier> result = tierMapper.selectList(new LambdaQueryWrapper<BillingPlanTier>()
                .eq(BillingPlanTier::getPlanId, planId)
                .orderByAsc(BillingPlanTier::getSortOrder, BillingPlanTier::getTierMin));
        return result == null ? List.of() : result;
    }

    private List<BillingMeteringPolicyDTO.SelectorDTO> buildSelectors(BillingPlanModel plan) {
        List<BillingMeteringPolicyDTO.SelectorDTO> result = new ArrayList<>();
        if (plan == null || plan.getMetering() == null) {
            throw BillingPlanException.conflict("BILLING_PLAN_CONFIG_INVALID", "计量配置损坏，无法解析计费策略");
        }
        List<BillingPlanModel.ConditionConfig> conditions = plan.getMetering().getConditions() != null
                ? plan.getMetering().getConditions() : List.of();
        for (int index = 0; index < conditions.size(); index++) {
            BillingPlanModel.ConditionConfig condition = conditions.get(index);
            if (condition == null) {
                throw BillingPlanException.conflict("BILLING_PLAN_CONFIG_INVALID", "计量条件损坏，无法解析计费策略");
            }
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
        if (template == null) throw BillingPlanException.badRequest("计费模板不存在或未启用");
        VendorInfoDTO vendor = requireData(safeCall(
                () -> vendorClient.getById(command.getVendorId()), "厂商服务暂不可用"), "厂商不存在");
        ApiInterfaceDTO apiInterface = requireData(safeCall(
                () -> interfaceClient.getById(command.getInterfaceId()), "接口服务暂不可用"), "接口不存在");
        Result<List<VendorConfigDTO>> bindings = safeCall(
                () -> vendorConfigClient.list(command.getVendorId(), null, command.getInterfaceId(), null),
                "厂商配置服务暂不可用");
        requireSuccessful(bindings, "厂商配置服务不可用");
        if (bindings.getData() == null || bindings.getData().isEmpty()) {
            throw BillingPlanException.badRequest("所选厂商未绑定到该接口");
        }
        InterfaceContractDTO contract = requireData(safeCall(
                () -> interfaceClient.getContract(command.getInterfaceId()), "接口服务暂不可用"),
                "接口响应契约不存在");
        command.setVendorCode(vendor.getVendorCode());
        command.setVendorName(vendor.getVendorName());
        command.setInterfaceCode(apiInterface.getInterfaceCode());
        command.setInterfaceName(apiInterface.getInterfaceName());
        command.setContractFingerprint(codec.sha256(contract.getResponseFields()));
        return new Enrichment(vendor, apiInterface, contract);
    }

    private <T> T requireData(Result<T> result, String message) {
        if (result == null) {
            throw BillingPlanException.unavailable(message + "，依赖服务无响应");
        }
        if (!Integer.valueOf(200).equals(result.getCode())) {
            if (Integer.valueOf(404).equals(result.getCode())) {
                throw BillingPlanException.notFound(message);
            }
            throw BillingPlanException.unavailable(message + "，依赖服务返回错误");
        }
        if (result.getData() == null) {
            throw BillingPlanException.notFound(message);
        }
        return result.getData();
    }

    private <T> Result<T> safeCall(Supplier<Result<T>> call, String message) {
        try {
            return call.get();
        } catch (RuntimeException exception) {
            throw BillingPlanException.unavailable(message);
        }
    }

    private void validateEnrichmentInputs(BillingPlanModel command) {
        List<String> errors = new ArrayList<>();
        if (command.getTemplateCode() == null || command.getTemplateCode().isBlank()) {
            errors.add("计费模板不能为空");
        }
        if (command.getVendorId() == null) errors.add("厂商不能为空");
        if (command.getInterfaceId() == null) errors.add("接口不能为空");
        if (!errors.isEmpty()) throw BillingPlanException.validation(errors);
    }

    private void requireSuccessful(Result<?> result, String message) {
        if (result == null || !Integer.valueOf(200).equals(result.getCode())) {
            throw BillingPlanException.unavailable(message);
        }
    }

    private BillingPlan requirePlan(Long id) {
        BillingPlan plan = planMapper.selectById(id);
        if (plan == null) throw BillingPlanException.notFound("计费方案不存在: " + id);
        return plan;
    }

    private BillingPlan requirePlanForUpdate(Long id) {
        BillingPlan plan = planMapper.selectByIdForUpdate(id);
        if (plan == null) throw BillingPlanException.notFound("计费方案不存在: " + id);
        return plan;
    }

    private int safeMaxVersion(String planCode) {
        Integer maxVersion = planMapper.selectMaxVersion(planCode);
        return maxVersion == null ? 0 : maxVersion;
    }

    private void requirePublishKey(BillingPlan plan) {
        List<String> errors = publishKeyErrors(plan);
        if (!errors.isEmpty()) throw BillingPlanException.validation(errors);
    }

    private List<String> publishKeyErrors(BillingPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan == null || plan.getPlanCode() == null || plan.getPlanCode().isBlank()) {
            errors.add("方案编码不能为空");
        }
        if (plan == null || plan.getVendorId() == null) errors.add("厂商不能为空");
        if (plan == null || plan.getInterfaceId() == null) errors.add("接口不能为空");
        if (plan == null || plan.getEffectiveFrom() == null) errors.add("生效时间不能为空");
        return errors;
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
        if (tierMapper.delete(new LambdaQueryWrapper<BillingPlanTier>()
                .eq(BillingPlanTier::getPlanId, planId)) < 0) {
            throw BillingPlanException.conflict(
                    "BILLING_PLAN_TIER_PERSIST_FAILED", "计费阶梯旧配置删除失败");
        }
        if (tiers == null) return;
        for (int index = 0; index < tiers.size(); index++) {
            BillingPlanModel.TierConfig source = tiers.get(index);
            if (source == null) {
                throw BillingPlanException.validation(List.of("阶梯配置不能为空"));
            }
            BillingPlanTier tier = new BillingPlanTier();
            tier.setPlanId(planId);
            tier.setTierMin(source.getTierMin());
            tier.setTierMax(source.getTierMax());
            tier.setUnitPrice(source.getUnitPrice());
            tier.setDiscount(source.getDiscount());
            tier.setSortOrder(index);
            if (tierMapper.insert(tier) <= 0) {
                throw BillingPlanException.conflict(
                        "BILLING_PLAN_TIER_PERSIST_FAILED", "计费阶梯保存失败");
            }
        }
    }

    private void closeSupersededVersion(BillingPlanModel candidate, List<BillingPlan> publishablePlans) {
        List<BillingPlan> versions = publishablePlans.stream()
                .filter(version -> candidate.getPlanCode().equals(version.getPlanCode())
                        && !candidate.getId().equals(version.getId()))
                .toList();
        for (BillingPlan version : versions) {
            if (overlaps(version.getEffectiveFrom(), version.getEffectiveTo(),
                    candidate.getEffectiveFrom(), candidate.getEffectiveTo())) {
                version.setEffectiveTo(candidate.getEffectiveFrom());
                LocalDateTime now = LocalDateTime.now();
                if (!candidate.getEffectiveFrom().isAfter(now)) version.setStatus("EXPIRED");
                version.setUpdatedAt(now);
                if (planMapper.updateById(version) <= 0) {
                    throw BillingPlanException.conflict(
                            "BILLING_PLAN_PERSIST_FAILED", "计费方案旧版本关闭失败");
                }
            }
        }
    }

    private void rejectPublishConflicts(BillingPlanModel candidate, List<BillingPlan> publishablePlans) {
        PublishConflict conflict = findPublishConflict(candidate, publishablePlans);
        if (conflict != null) {
            throw BillingPlanException.conflict(conflict.code(), conflict.message());
        }
    }

    private PublishConflict findPublishConflict(BillingPlanModel candidate,
                                                List<BillingPlan> publishablePlans) {
        if (candidate == null || publishablePlans == null) {
            return new PublishConflict("BILLING_PLAN_DATA_CONFLICT", "计费方案发布数据不完整");
        }
        for (BillingPlan existing : publishablePlans) {
            if (existing == null) {
                return new PublishConflict("BILLING_PLAN_DATA_CONFLICT", "计费方案发布数据损坏");
            }
            if (candidate.getId() != null && candidate.getId().equals(existing.getId())) continue;
            if (candidate.getPlanCode() == null || existing.getPlanCode() == null) {
                return new PublishConflict("BILLING_PLAN_DATA_CONFLICT", "计费方案编码数据损坏");
            }
            if (candidate.getPlanCode().equals(existing.getPlanCode())) {
                if (existing.getEffectiveFrom() == null
                        || !candidate.getEffectiveFrom().isAfter(existing.getEffectiveFrom())) {
                    return new PublishConflict("BILLING_PLAN_VERSION_ORDER",
                            "同一方案的新版本生效时间必须晚于已有版本");
                }
                continue;
            }
            if (overlaps(existing.getEffectiveFrom(), existing.getEffectiveTo(),
                    candidate.getEffectiveFrom(), candidate.getEffectiveTo())) {
                return new PublishConflict("BILLING_PLAN_EFFECTIVE_OVERLAP",
                        "同一厂商、接口和计费方向存在生效区间重叠的方案");
            }
        }
        return null;
    }

    private boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd,
                             LocalDateTime rightStart, LocalDateTime rightEnd) {
        if (leftStart == null || rightStart == null) {
            throw BillingPlanException.conflict("BILLING_PLAN_DATA_CONFLICT",
                    "计费方案生效时间数据损坏");
        }
        return (leftEnd == null || rightStart.isBefore(leftEnd))
                && (rightEnd == null || leftStart.isBefore(rightEnd));
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> safeErrors(List<String> errors) {
        return errors == null ? List.of("计费方案校验失败") : errors;
    }

    private record Enrichment(VendorInfoDTO vendor, ApiInterfaceDTO apiInterface,
                              InterfaceContractDTO contract) {
    }

    private record PublishConflict(String code, String message) {
    }
}
