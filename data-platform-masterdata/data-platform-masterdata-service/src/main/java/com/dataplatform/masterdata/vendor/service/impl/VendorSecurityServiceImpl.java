package com.dataplatform.masterdata.vendor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.common.security.pipeline.SecurityDirection;
import com.dataplatform.common.security.pipeline.SecurityExecutionContext;
import com.dataplatform.common.security.pipeline.SecurityPipelineExecutor;
import com.dataplatform.common.security.pipeline.SecurityStepConfig;
import com.dataplatform.common.security.pipeline.SecurityStepType;
import com.dataplatform.masterdata.vendor.api.dto.VendorRuntimeSecurityDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityCapabilityDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityOrderReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepListDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityVersionDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.entity.VendorSecurityStep;
import com.dataplatform.masterdata.vendor.entity.VendorSecurityVersion;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorSecurityStepMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorSecurityVersionMapper;
import com.dataplatform.masterdata.vendor.service.SecurityConfigConflictException;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import com.dataplatform.masterdata.vendor.service.VendorSecurityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorSecurityServiceImpl implements VendorSecurityService {

    private final VendorSecurityStepMapper stepMapper;
    private final VendorSecurityVersionMapper versionMapper;
    private final VendorConfigMapper vendorConfigMapper;
    private final VendorInfoMapper vendorInfoMapper;
    private final VendorExtendedConfigService extendedConfigService;
    private final ObjectMapper objectMapper;
    private final SecurityPipelineExecutor pipelineExecutor = new SecurityPipelineExecutor();

    public VendorSecurityServiceImpl(VendorSecurityStepMapper stepMapper,
                                     VendorSecurityVersionMapper versionMapper,
                                     VendorConfigMapper vendorConfigMapper,
                                     VendorInfoMapper vendorInfoMapper,
                                     VendorExtendedConfigService extendedConfigService,
                                     ObjectMapper objectMapper) {
        this.stepMapper = stepMapper;
        this.versionMapper = versionMapper;
        this.vendorConfigMapper = vendorConfigMapper;
        this.vendorInfoMapper = vendorInfoMapper;
        this.extendedConfigService = extendedConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public VendorSecurityStepListDTO getSteps(Long vendorConfigId) {
        VendorConfig config = requireConfig(vendorConfigId);
        VendorSecurityStepListDTO result = new VendorSecurityStepListDTO();
        result.setVersion(currentVersion(config));
        result.setSteps(loadEntities(vendorConfigId).stream().map(this::toDTO).toList());
        return result;
    }

    @Override
    @Transactional
    public VendorSecurityStepListDTO replaceSteps(Long vendorConfigId, Integer expectedVersion,
                                                  List<VendorSecurityStepDTO> steps) {
        VendorConfig config = requireConfig(vendorConfigId);
        int nextVersion = incrementVersion(config, expectedVersion);
        List<VendorSecurityStepDTO> normalized = normalize(steps);
        validate(normalized);
        stepMapper.delete(new LambdaQueryWrapper<VendorSecurityStep>()
                .eq(VendorSecurityStep::getVendorConfigId, vendorConfigId));
        for (VendorSecurityStepDTO step : normalized) {
            stepMapper.insert(toEntity(vendorConfigId, step));
        }
        List<VendorSecurityStepDTO> saved = loadEntities(vendorConfigId).stream().map(this::toDTO).toList();
        saveSnapshot(vendorConfigId, nextVersion, saved);
        VendorSecurityStepListDTO result = new VendorSecurityStepListDTO();
        result.setVersion(nextVersion);
        result.setSteps(saved);
        return result;
    }

    @Override
    @Transactional
    public VendorSecurityStepListDTO reorder(Long vendorConfigId, VendorSecurityOrderReqDTO request) {
        VendorConfig config = requireConfig(vendorConfigId);
        SecurityDirection direction = parseDirection(request.getDirection());
        List<VendorSecurityStep> steps = loadEntities(vendorConfigId).stream()
                .filter(step -> direction.name().equals(step.getDirection()))
                .toList();
        Set<Long> currentIds = steps.stream().map(VendorSecurityStep::getId).collect(Collectors.toSet());
        if (request.getOrderedStepIds().size() != currentIds.size()
                || !currentIds.equals(new HashSet<>(request.getOrderedStepIds()))) {
            throw new IllegalArgumentException("排序步骤必须与当前方向全部步骤完全一致");
        }
        int nextVersion = incrementVersion(config, request.getVersion());
        int temporarySort = -100;
        for (Long id : request.getOrderedStepIds()) {
            VendorSecurityStep update = new VendorSecurityStep();
            update.setId(id);
            update.setSortNo(temporarySort);
            stepMapper.updateById(update);
            temporarySort -= 100;
        }
        int sortNo = 100;
        for (Long id : request.getOrderedStepIds()) {
            VendorSecurityStep update = new VendorSecurityStep();
            update.setId(id);
            update.setSortNo(sortNo);
            stepMapper.updateById(update);
            sortNo += 100;
        }
        List<VendorSecurityStepDTO> saved = loadEntities(vendorConfigId).stream().map(this::toDTO).toList();
        validate(saved);
        saveSnapshot(vendorConfigId, nextVersion, saved);
        VendorSecurityStepListDTO result = new VendorSecurityStepListDTO();
        result.setVersion(nextVersion);
        result.setSteps(saved);
        return result;
    }

    @Override
    public VendorSecurityPreviewDTO preview(Long vendorConfigId, VendorSecurityPreviewReqDTO request) {
        SecurityDirection direction = parseDirection(request.getDirection());
        List<SecurityStepConfig> runtimeSteps;
        if (request.getSteps() != null) {
            List<VendorSecurityStepDTO> normalized = normalize(request.getSteps());
            validate(normalized);
            runtimeSteps = normalized.stream().map(this::toRuntime).toList();
        } else {
            runtimeSteps = getRuntimeSteps(vendorConfigId);
        }
        SecurityExecutionContext context = new SecurityExecutionContext(direction,
                request.getParams(), request.getHeaders(), request.getQuery(),
                resolveSecrets(vendorConfigId, runtimeSteps));
        context.setBody(request.getBody());
        pipelineExecutor.execute(direction, runtimeSteps, context);
        VendorSecurityPreviewDTO result = new VendorSecurityPreviewDTO();
        result.setParams(new LinkedHashMap<>(context.getParams()));
        result.setHeaders(new LinkedHashMap<>(context.getHeaders()));
        result.setQuery(new LinkedHashMap<>(context.getQuery()));
        result.setBody(context.getBody());
        result.setStepResults(new LinkedHashMap<>(context.getResults()));
        return result;
    }

    @Override
    public List<VendorSecurityCapabilityDTO> capabilities() {
        List<VendorSecurityCapabilityDTO> result = new ArrayList<>();
        result.add(capability("FIELD_SELECT", "字段选择", List.of("REQUEST", "RESPONSE"), List.of(),
                Map.of("fields", List.of(), "replaceParams", false)));
        result.add(capability("GENERATE", "生成时间戳/随机数", List.of("REQUEST"),
                List.of("TIMESTAMP_SECONDS", "TIMESTAMP_MILLIS", "UUID", "NONCE", "CONSTANT"),
                Map.of("generator", "TIMESTAMP_MILLIS", "location", "PARAM")));
        result.add(capability("CANONICALIZE", "字段规范化", List.of("REQUEST", "RESPONSE"),
                List.of("KEY_ASC", "KEY_DESC", "EXPLICIT", "NONE"),
                Map.of("inputFrom", "PARAMS", "fieldOrder", "KEY_ASC", "pairSeparator", "&",
                        "keyValueSeparator", "=", "nullPolicy", "IGNORE")));
        result.add(capability("DIGEST", "摘要", List.of("REQUEST", "RESPONSE"),
                List.of("SHA256", "SHA512", "SM3", "SHA1", "MD5"),
                Map.of("algorithm", "SHA256", "outputEncoding", "HEX_LOWER")));
        result.add(capability("HMAC", "消息认证码", List.of("REQUEST", "RESPONSE"),
                List.of("HMAC_SHA256", "HMAC_SHA512", "HMAC_SHA1"),
                Map.of("algorithm", "HMAC_SHA256", "outputEncoding", "HEX_LOWER")));
        result.add(capability("SIGN", "非对称签名", List.of("REQUEST"), List.of("RSA_SHA256"),
                Map.of("algorithm", "RSA_SHA256", "outputEncoding", "BASE64")));
        result.add(capability("VERIFY", "响应验签", List.of("RESPONSE"),
                List.of("RSA_SHA256", "HMAC_SHA256", "HMAC_SHA512", "HMAC_SHA1"),
                Map.of("algorithm", "RSA_SHA256", "signatureEncoding", "BASE64", "failOnInvalid", true)));
        result.add(capability("ENCRYPT", "加密", List.of("REQUEST"),
                List.of("AES_GCM", "AES_CBC", "RSA_OAEP", "SM4_CBC"),
                Map.of("algorithm", "AES_GCM", "outputEncoding", "BASE64", "prependIv", true)));
        result.add(capability("DECRYPT", "响应解密", List.of("RESPONSE"),
                List.of("AES_GCM", "AES_CBC", "RSA_OAEP", "SM4_CBC"),
                Map.of("algorithm", "AES_GCM", "inputEncoding", "BASE64", "prependIv", true)));
        result.add(capability("ENCODE", "编码", List.of("REQUEST", "RESPONSE"),
                List.of("BASE64", "BASE64_URL", "HEX_LOWER", "HEX_UPPER"), Map.of("encoding", "BASE64")));
        result.add(capability("DECODE", "解码", List.of("RESPONSE"),
                List.of("BASE64", "BASE64_URL", "HEX_LOWER", "HEX_UPPER"), Map.of("encoding", "BASE64")));
        result.add(capability("INJECT", "写入请求/响应", List.of("REQUEST", "RESPONSE"),
                List.of("PARAM", "HEADER", "QUERY", "BODY"), Map.of("location", "PARAM")));
        result.add(capability("REMOVE_FIELD", "移除临时字段", List.of("REQUEST", "RESPONSE"),
                List.of("PARAM", "HEADER", "QUERY"), Map.of("location", "PARAM")));
        return result;
    }

    @Override
    public List<VendorSecurityVersionDTO> history(Long vendorConfigId) {
        requireConfig(vendorConfigId);
        return versionMapper.selectList(new LambdaQueryWrapper<VendorSecurityVersion>()
                        .eq(VendorSecurityVersion::getVendorConfigId, vendorConfigId)
                        .orderByDesc(VendorSecurityVersion::getVersionNo))
                .stream().map(version -> {
                    VendorSecurityVersionDTO dto = new VendorSecurityVersionDTO();
                    dto.setId(version.getId());
                    dto.setVersion(version.getVersionNo());
                    dto.setCreatedAt(version.getCreatedAt());
                    return dto;
                }).toList();
    }

    @Override
    @Transactional
    public VendorSecurityStepListDTO rollback(Long vendorConfigId, Long versionId, Integer expectedVersion) {
        VendorSecurityVersion version = versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(vendorConfigId, version.getVendorConfigId())) {
            throw new IllegalArgumentException("安全配置版本不存在");
        }
        try {
            List<VendorSecurityStepDTO> steps = objectMapper.readValue(version.getConfigSnapshot(),
                    new TypeReference<List<VendorSecurityStepDTO>>() { });
            return replaceSteps(vendorConfigId, expectedVersion, steps);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("安全配置版本内容损坏", e);
        }
    }

    @Override
    public VendorRuntimeSecurityDTO getRuntimeSecurity(Long vendorConfigId) {
        VendorRuntimeSecurityDTO result = new VendorRuntimeSecurityDTO();
        result.setSteps(getSteps(vendorConfigId).getSteps());
        result.setResolvedSecrets(resolveSecrets(vendorConfigId));
        return result;
    }

    @Override
    public List<SecurityStepConfig> getRuntimeSteps(Long vendorConfigId) {
        return getSteps(vendorConfigId).getSteps().stream().map(this::toRuntime).toList();
    }

    @Override
    public Map<String, String> resolveSecrets(Long vendorConfigId) {
        return resolveSecrets(vendorConfigId, getRuntimeSteps(vendorConfigId));
    }

    private Map<String, String> resolveSecrets(Long vendorConfigId, List<SecurityStepConfig> steps) {
        VendorConfig config = requireConfig(vendorConfigId);
        Set<String> refs = steps.stream()
                .map(SecurityStepConfig::getConfig)
                .map(stepConfig -> stepConfig.get("secretRef"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(ref -> !ref.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        if (refs.isEmpty()) {
            return Map.of();
        }
        VendorInfo vendor = vendorInfoMapper.selectById(config.getVendorId());
        Map<String, String> secrets = new LinkedHashMap<>();
        for (String ref : refs) {
            String value;
            if ("vendor.secretKey".equals(ref)) {
                value = vendor == null ? null : vendor.getSecretKey();
            } else {
                value = extendedConfigService.getConfig(config.getVendorId(), ref);
            }
            if (value != null) {
                secrets.put(ref, value);
            }
        }
        return secrets;
    }

    private VendorConfig requireConfig(Long vendorConfigId) {
        VendorConfig config = vendorConfigMapper.selectById(vendorConfigId);
        if (config == null) {
            throw new IllegalArgumentException("厂商接口配置不存在");
        }
        return config;
    }

    private List<VendorSecurityStep> loadEntities(Long vendorConfigId) {
        return stepMapper.selectList(new LambdaQueryWrapper<VendorSecurityStep>()
                .eq(VendorSecurityStep::getVendorConfigId, vendorConfigId)
                .orderByAsc(VendorSecurityStep::getDirection)
                .orderByAsc(VendorSecurityStep::getSortNo));
    }

    private int currentVersion(VendorConfig config) {
        return config.getSecurityVersion() == null ? 0 : config.getSecurityVersion();
    }

    private int incrementVersion(VendorConfig config, Integer expectedVersion) {
        int current = currentVersion(config);
        if (expectedVersion == null || expectedVersion != current) {
            throw new SecurityConfigConflictException("安全配置已被其他用户修改，请刷新后重试");
        }
        boolean updated = vendorConfigMapper.update(null, new LambdaUpdateWrapper<VendorConfig>()
                .eq(VendorConfig::getId, config.getId())
                .eq(VendorConfig::getSecurityVersion, current)
                .set(VendorConfig::getSecurityVersion, current + 1)) > 0;
        if (!updated) {
            throw new SecurityConfigConflictException("安全配置已被其他用户修改，请刷新后重试");
        }
        return current + 1;
    }

    private List<VendorSecurityStepDTO> normalize(List<VendorSecurityStepDTO> steps) {
        List<VendorSecurityStepDTO> source = steps == null ? List.of() : steps;
        Map<String, Integer> counters = new LinkedHashMap<>();
        List<VendorSecurityStepDTO> result = new ArrayList<>();
        for (VendorSecurityStepDTO input : source) {
            VendorSecurityStepDTO step = new VendorSecurityStepDTO();
            String direction = parseDirection(input.getDirection()).name();
            int sequence = counters.merge(direction, 1, Integer::sum);
            step.setId(input.getId());
            step.setStepKey(input.getStepKey() == null || input.getStepKey().isBlank()
                    ? "step-" + UUID.randomUUID() : input.getStepKey());
            step.setDirection(direction);
            step.setStepType(parseType(input.getStepType()).name());
            step.setStepName(input.getStepName());
            step.setSortNo(sequence * 100);
            step.setEnabled(!Boolean.FALSE.equals(input.getEnabled()));
            step.setConfig(input.getConfig());
            result.add(step);
        }
        return result;
    }

    private void validate(List<VendorSecurityStepDTO> steps) {
        Set<String> scopedKeys = new HashSet<>();
        for (VendorSecurityStepDTO step : steps) {
            String scope = step.getDirection() + ":" + step.getStepKey();
            if (!scopedKeys.add(scope)) {
                throw new IllegalArgumentException("同一方向的步骤标识不能重复: " + step.getStepKey());
            }
        }
        for (SecurityDirection direction : SecurityDirection.values()) {
            List<SecurityStepConfig> runtime = steps.stream()
                    .filter(step -> direction.name().equals(step.getDirection()))
                    .map(this::toRuntime)
                    .sorted(Comparator.comparing(SecurityStepConfig::getSortNo))
                    .toList();
            pipelineExecutor.validate(direction, runtime);
        }
    }

    private VendorSecurityStepDTO toDTO(VendorSecurityStep entity) {
        VendorSecurityStepDTO dto = new VendorSecurityStepDTO();
        dto.setId(entity.getId());
        dto.setStepKey(entity.getStepKey());
        dto.setDirection(entity.getDirection());
        dto.setStepType(entity.getStepType());
        dto.setStepName(entity.getStepName());
        dto.setSortNo(entity.getSortNo());
        dto.setEnabled(entity.getEnabled());
        try {
            dto.setConfig(objectMapper.readValue(entity.getConfigJson(), new TypeReference<Map<String, Object>>() { }));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("安全步骤配置格式错误: " + entity.getStepKey(), e);
        }
        return dto;
    }

    private VendorSecurityStep toEntity(Long vendorConfigId, VendorSecurityStepDTO dto) {
        VendorSecurityStep entity = new VendorSecurityStep();
        entity.setVendorConfigId(vendorConfigId);
        entity.setStepKey(dto.getStepKey());
        entity.setDirection(dto.getDirection());
        entity.setStepType(dto.getStepType());
        entity.setStepName(dto.getStepName());
        entity.setSortNo(dto.getSortNo());
        entity.setEnabled(dto.getEnabled());
        try {
            entity.setConfigJson(objectMapper.writeValueAsString(dto.getConfig()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("安全步骤配置无法序列化", e);
        }
        return entity;
    }

    private SecurityStepConfig toRuntime(VendorSecurityStepDTO dto) {
        SecurityStepConfig runtime = new SecurityStepConfig();
        runtime.setId(dto.getStepKey());
        runtime.setDirection(parseDirection(dto.getDirection()));
        runtime.setStepType(parseType(dto.getStepType()));
        runtime.setStepName(dto.getStepName());
        runtime.setSortNo(dto.getSortNo());
        runtime.setEnabled(dto.getEnabled());
        runtime.setConfig(dto.getConfig());
        return runtime;
    }

    private void saveSnapshot(Long vendorConfigId, int version, List<VendorSecurityStepDTO> steps) {
        VendorSecurityVersion snapshot = new VendorSecurityVersion();
        snapshot.setVendorConfigId(vendorConfigId);
        snapshot.setVersionNo(version);
        try {
            snapshot.setConfigSnapshot(objectMapper.writeValueAsString(steps));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("安全配置版本无法序列化", e);
        }
        versionMapper.insert(snapshot);
    }

    private VendorSecurityCapabilityDTO capability(String type, String name, List<String> directions,
                                                    List<String> algorithms, Map<String, Object> defaults) {
        VendorSecurityCapabilityDTO dto = new VendorSecurityCapabilityDTO();
        dto.setStepType(type);
        dto.setName(name);
        dto.setDirections(directions);
        dto.setAlgorithms(algorithms);
        dto.setDefaults(defaults);
        return dto;
    }

    private SecurityDirection parseDirection(String direction) {
        try {
            return SecurityDirection.valueOf((direction == null ? "REQUEST" : direction).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的安全流水线方向: " + direction, e);
        }
    }

    private SecurityStepType parseType(String type) {
        try {
            return SecurityStepType.valueOf(type == null ? "" : type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的安全步骤类型: " + type, e);
        }
    }
}
