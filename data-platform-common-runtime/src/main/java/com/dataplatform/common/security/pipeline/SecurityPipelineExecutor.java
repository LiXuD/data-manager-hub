package com.dataplatform.common.security.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SecurityPipelineExecutor {

    private static final int MAX_STEP_COUNT = 100;
    private static final int MAX_STEP_CONFIG_BYTES = 64 * 1024;
    private static final int MAX_PIPELINE_CONFIG_BYTES = 256 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<SecurityStepType, SecurityStepHandler> handlers = new EnumMap<>(SecurityStepType.class);

    public SecurityPipelineExecutor() {
        this(DefaultSecurityStepHandlers.create());
    }

    public SecurityPipelineExecutor(List<SecurityStepHandler> handlers) {
        for (SecurityStepHandler handler : handlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

    public SecurityExecutionContext execute(SecurityDirection direction,
                                            List<SecurityStepConfig> steps,
                                            SecurityExecutionContext context) {
        if (steps == null || steps.isEmpty()) {
            return context;
        }
        List<SecurityStepConfig> ordered = steps.stream()
                .filter(step -> step.getDirection() == direction)
                .filter(step -> !Boolean.FALSE.equals(step.getEnabled()))
                .sorted(Comparator.comparing(SecurityStepConfig::getSortNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        validate(direction, ordered);
        for (SecurityStepConfig step : ordered) {
            SecurityStepHandler handler = handlers.get(step.getStepType());
            Object result = handler.execute(context, step);
            context.record(step.getId(), result);
        }
        return context;
    }

    public void validate(SecurityDirection direction, List<SecurityStepConfig> steps) {
        if (steps == null) {
            throw new IllegalArgumentException("安全步骤列表不能为空");
        }
        if (steps.size() > MAX_STEP_COUNT) {
            throw new IllegalArgumentException("安全流水线步骤不能超过" + MAX_STEP_COUNT + "个");
        }
        Set<String> available = new HashSet<>(Set.of("PARAMS", "MAPPED_PARAMS", "RESPONSE",
                "BODY", "RESPONSE_BODY", "HEADERS", "QUERY", "LAST"));
        Set<Integer> sortNumbers = new HashSet<>();
        int totalConfigBytes = 0;
        for (SecurityStepConfig step : new ArrayList<>(steps)) {
            if (step == null) {
                throw new IllegalArgumentException("安全步骤不能为空");
            }
            int configBytes = configSize(step.getConfig());
            if (configBytes > MAX_STEP_CONFIG_BYTES) {
                throw new IllegalArgumentException("单个安全步骤配置不能超过" + MAX_STEP_CONFIG_BYTES + "字节");
            }
            totalConfigBytes += configBytes;
            if (totalConfigBytes > MAX_PIPELINE_CONFIG_BYTES) {
                throw new IllegalArgumentException("安全流水线配置不能超过" + MAX_PIPELINE_CONFIG_BYTES + "字节");
            }
            if (step.getStepType() == null) {
                throw new IllegalArgumentException("安全步骤类型不能为空");
            }
            if (step.getDirection() != direction) {
                throw new IllegalArgumentException("安全步骤方向不一致: " + step.getId());
            }
            if (step.getSortNo() == null || !sortNumbers.add(step.getSortNo())) {
                throw new IllegalArgumentException("安全步骤排序值为空或重复: " + step.getSortNo());
            }
            if (Boolean.FALSE.equals(step.getEnabled())) {
                continue;
            }
            String inputFrom = string(step.getConfig(), "inputFrom", null);
            validateReference(inputFrom, available, "inputFrom");
            if (step.getStepType() == SecurityStepType.VERIFY) {
                validateReference(string(step.getConfig(), "signatureFrom", null), available, "signatureFrom");
            }
            if ((step.getStepType() == SecurityStepType.DECRYPT || step.getStepType() == SecurityStepType.VERIFY)
                    && direction != SecurityDirection.RESPONSE) {
                throw new IllegalArgumentException(step.getStepType() + " 只能用于响应流水线");
            }
            if ((step.getStepType() == SecurityStepType.GENERATE
                    || step.getStepType() == SecurityStepType.SIGN
                    || step.getStepType() == SecurityStepType.ENCRYPT)
                    && direction != SecurityDirection.REQUEST) {
                throw new IllegalArgumentException(step.getStepType() + " 只能用于请求流水线");
            }
            if (step.getStepType() == SecurityStepType.DECODE && direction != SecurityDirection.RESPONSE) {
                throw new IllegalArgumentException("DECODE 只能用于响应流水线");
            }
            SecurityStepHandler handler = handlers.get(step.getStepType());
            if (handler == null) {
                throw new IllegalArgumentException("不支持的安全步骤: " + step.getStepType());
            }
            handler.validate(step);
            if (step.getId() != null && !step.getId().isBlank()) {
                if (!available.add(step.getId())) {
                    throw new IllegalArgumentException("安全步骤ID重复: " + step.getId());
                }
            }
        }
    }

    private int configSize(Map<String, Object> config) {
        try {
            return OBJECT_MAPPER.writeValueAsString(config == null ? Map.of() : config)
                    .getBytes(StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("安全步骤配置无法序列化", exception);
        }
    }

    private void validateReference(String reference, Set<String> available, String fieldName) {
        if (reference != null && !available.contains(reference) && !isScopedInput(reference, available)) {
            throw new IllegalArgumentException(fieldName + "引用尚未产生的输入: " + reference);
        }
    }

    static String string(Map<String, Object> config, String key, String defaultValue) {
        Object value = config == null ? null : config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private boolean isScopedInput(String inputFrom, Set<String> available) {
        int separator = inputFrom.indexOf('.');
        if (separator <= 0) {
            return false;
        }
        String scope = inputFrom.substring(0, separator).toUpperCase();
        return Set.of("PARAMS", "MAPPED_PARAMS", "RESPONSE", "HEADERS", "QUERY", "RESULT").contains(scope)
                && (!"RESULT".equals(scope) || available.contains(inputFrom.substring(separator + 1)));
    }
}
