package com.dataplatform.billing.service;

import com.dataplatform.billing.api.dto.BillingChargeReqDTO;
import com.dataplatform.billing.model.BillingPlanModel;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 安全、有限的计量条件 DSL；不执行脚本或表达式语言。 */
@Component
public class BillingMeteringEvaluator {

    public Evaluation evaluate(BillingPlanModel plan, BillingChargeReqDTO request) {
        BillingPlanModel.MeteringConfig metering = plan.getMetering();
        BillingPlanModel.AdjustmentConfig adjustment = plan.getAdjustment();
        List<String> decisions = new ArrayList<>();

        if (Boolean.TRUE.equals(adjustment.getNoChargeOnFailure())
                && !Boolean.TRUE.equals(request.getSuccess())) {
            decisions.add("调用失败：按方案配置不收费");
            return new Evaluation(false, false, BigDecimal.ZERO, decisions);
        }
        if (Boolean.TRUE.equals(adjustment.getRequireValidContract())
                && !Boolean.TRUE.equals(request.getResponseContractValid())) {
            decisions.add("响应契约校验失败：进入待复核");
            return new Evaluation(false, true, BigDecimal.ZERO, decisions);
        }
        String cachePolicy = normalize(metering.getCacheBillingPolicy(), "FREE");
        if (Boolean.TRUE.equals(request.getCached()) && "FREE".equals(cachePolicy)) {
            decisions.add("命中缓存：按FREE策略不收费");
            return new Evaluation(false, false, BigDecimal.ZERO, decisions);
        }

        List<Boolean> matches = new ArrayList<>();
        List<BillingPlanModel.ConditionConfig> conditions = metering.getConditions() != null
                ? metering.getConditions() : List.of();
        for (int index = 0; index < conditions.size(); index++) {
            BillingPlanModel.ConditionConfig condition = conditions.get(index);
            String alias = condition.getAlias() != null && !condition.getAlias().isBlank()
                    ? condition.getAlias() : "condition-" + index;
            Value value = resolveValue(condition.getSource(), condition.getPath(), alias, request);
            if (!value.present()) {
                MissingDecision missing = missingDecision(metering.getMissingFieldPolicy(), alias, decisions);
                if (missing.pendingReview()) {
                    return new Evaluation(false, true, BigDecimal.ZERO, decisions);
                }
                matches.add(missing.matches());
                continue;
            }
            boolean matched = compare(value.value(), condition.getOperator(), condition.getExpectedValue());
            matches.add(matched);
            decisions.add(alias + " " + normalize(condition.getOperator(), "EQ")
                    + " => " + (matched ? "通过" : "不通过"));
        }

        boolean billable = conditions.isEmpty()
                || ("OR".equals(normalize(metering.getLogic(), "AND"))
                    ? matches.stream().anyMatch(Boolean.TRUE::equals)
                    : matches.stream().allMatch(Boolean.TRUE::equals));
        if (!billable) {
            decisions.add("计量条件未满足：不收费");
            return new Evaluation(false, false, BigDecimal.ZERO, decisions);
        }

        BillingPlanModel.QuantityConfig quantityConfig = metering.getQuantity() != null
                ? metering.getQuantity() : new BillingPlanModel.QuantityConfig();
        BigDecimal quantity = resolveQuantity(quantityConfig, request, metering, decisions);
        if (quantity == null) {
            return new Evaluation(false, true, BigDecimal.ZERO, decisions);
        }
        if (quantity.signum() < 0) {
            throw new IllegalArgumentException("计费数量不能为负数");
        }
        decisions.add("计费数量=" + quantity.stripTrailingZeros().toPlainString());
        return new Evaluation(true, false, quantity, decisions);
    }

    private BigDecimal resolveQuantity(BillingPlanModel.QuantityConfig quantity,
                                       BillingChargeReqDTO request,
                                       BillingPlanModel.MeteringConfig metering,
                                       List<String> decisions) {
        String type = normalize(quantity.getType(), "FIXED");
        if ("FIXED".equals(type)) {
            return quantity.getFixedValue() != null ? quantity.getFixedValue() : BigDecimal.ONE;
        }
        if ("DURATION".equals(type)) {
            return request.getLatencyMs() != null ? BigDecimal.valueOf(request.getLatencyMs()) : BigDecimal.ZERO;
        }
        String alias = quantity.getAlias() != null && !quantity.getAlias().isBlank()
                ? quantity.getAlias() : "quantity";
        Value value = resolveValue(quantity.getSource(), quantity.getPath(), alias, request);
        if (!value.present()) {
            MissingDecision missing = missingDecision(metering.getMissingFieldPolicy(), alias, decisions);
            if (missing.pendingReview()) {
                return null;
            }
            return missing.matches() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value.value() instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value.value() instanceof Collection<?> collection) {
            return BigDecimal.valueOf(collection.size());
        }
        if (value.value() != null && value.value().getClass().isArray()) {
            return BigDecimal.valueOf(Array.getLength(value.value()));
        }
        try {
            return new BigDecimal(String.valueOf(value.value()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("计费数量字段不是数值: " + alias);
        }
    }

    private Value resolveValue(String source, String path, String alias, BillingChargeReqDTO request) {
        String normalizedSource = normalize(source, "NORMALIZED_RESPONSE");
        if ("METADATA".equals(normalizedSource)) {
            Object value = switch (path == null ? "" : path) {
                case "success", "$.success" -> request.getSuccess();
                case "cached", "$.cached" -> request.getCached();
                case "responseContractValid", "$.responseContractValid" -> request.getResponseContractValid();
                case "latencyMs", "$.latencyMs" -> request.getLatencyMs();
                case "httpStatus", "$.httpStatus" -> request.getHttpStatus();
                default -> null;
            };
            return new Value(value != null, value);
        }
        Map<String, Object> facts = request.getMeteringFacts();
        return new Value(facts != null && facts.containsKey(alias), facts != null ? facts.get(alias) : null);
    }

    private MissingDecision missingDecision(String policy, String alias, List<String> decisions) {
        return switch (normalize(policy, "PENDING_REVIEW")) {
            case "NOT_BILLABLE" -> {
                decisions.add(alias + " 缺失：按NOT_BILLABLE处理");
                yield new MissingDecision(false, false);
            }
            case "BILLABLE" -> {
                decisions.add(alias + " 缺失：按BILLABLE处理");
                yield new MissingDecision(true, false);
            }
            case "ERROR" -> throw new IllegalArgumentException("计量字段缺失: " + alias);
            default -> {
                decisions.add(alias + " 缺失：进入待复核");
                yield new MissingDecision(false, true);
            }
        };
    }

    private boolean compare(Object actual, String operator, Object expected) {
        return switch (normalize(operator, "EQ")) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "EMPTY" -> isEmpty(actual);
            case "NOT_EMPTY" -> !isEmpty(actual);
            case "TRUE" -> Boolean.TRUE.equals(asBoolean(actual));
            case "FALSE" -> Boolean.FALSE.equals(asBoolean(actual));
            case "IN" -> asCollection(expected).stream().anyMatch(item -> equalValue(actual, item));
            case "NOT_IN" -> asCollection(expected).stream().noneMatch(item -> equalValue(actual, item));
            case "NE" -> !equalValue(actual, expected);
            case "GT" -> compareValue(actual, expected) > 0;
            case "GTE" -> compareValue(actual, expected) >= 0;
            case "LT" -> compareValue(actual, expected) < 0;
            case "LTE" -> compareValue(actual, expected) <= 0;
            default -> equalValue(actual, expected);
        };
    }

    private boolean equalValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString())) == 0;
            } catch (NumberFormatException ignored) {
                // Fall through to normalized string equality.
            }
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            return asBoolean(actual).equals(asBoolean(expected));
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private int compareValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            throw new IllegalArgumentException("大小比较不支持null");
        }
        try {
            return new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString()));
        } catch (NumberFormatException exception) {
            return String.valueOf(actual).compareTo(String.valueOf(expected));
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence chars) return chars.isEmpty();
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

    private Boolean asBoolean(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.valueOf(String.valueOf(value));
    }

    private Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) return collection;
        return value == null ? List.of() : List.of(value);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
    }

    public record Evaluation(boolean billable, boolean pendingReview, BigDecimal quantity,
                             List<String> decisions) {
    }

    private record Value(boolean present, Object value) {
    }

    private record MissingDecision(boolean matches, boolean pendingReview) {
    }
}
