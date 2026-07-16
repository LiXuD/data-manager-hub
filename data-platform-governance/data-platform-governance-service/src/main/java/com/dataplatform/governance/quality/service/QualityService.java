package com.dataplatform.governance.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.governance.quality.entity.QualityRule;
import com.dataplatform.governance.quality.entity.QualityScore;
import com.dataplatform.governance.quality.mapper.QualityRuleMapper;
import com.dataplatform.governance.quality.mapper.QualityScoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 观测治理域数据质量的 Quality Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class QualityService extends ServiceImpl<QualityRuleMapper, QualityRule> {

    private static final Pattern COMPARISON = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_.]*)\\s*(==|=|!=|>=|<=|>|<)\\s*(.+)$");
    private static final Pattern REGEX = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_.]*)\\s*=~\\s*(.+)$");

    @Autowired
    private QualityScoreMapper qualityScoreMapper;

    public List<QualityRule> getActiveRules(String dataType) {
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityRule::getIsActive, true);
        if (dataType != null) {
            wrapper.eq(QualityRule::getDataType, dataType);
        }
        return this.list(wrapper);
    }

    public boolean addRule(QualityRule rule) {
        validateRule(rule);
        rule.setIsActive(true);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return this.save(rule);
    }

    public QualityScore checkQuality(String dataType, Long dataId, Map<String, Object> data) {
        List<QualityRule> rules = getActiveRules(dataType);
        if (rules.isEmpty()) {
            throw new IllegalStateException("数据类型没有启用的质量规则: " + dataType);
        }

        int passCount = 0;
        int failCount = 0;
        StringBuilder issues = new StringBuilder();

        for (QualityRule rule : rules) {
            boolean passed = evaluateRule(rule, data);
            if (passed) {
                passCount++;
            } else {
                failCount++;
                if (issues.length() > 0) issues.append("; ");
                issues.append(rule.getRuleName());
            }
        }

        double score = (double) passCount / rules.size() * 100;

        QualityScore qualityScore = new QualityScore();
        qualityScore.setDataType(dataType);
        qualityScore.setDataId(dataId);
        qualityScore.setScore(score);
        qualityScore.setPassCount(passCount);
        qualityScore.setFailCount(failCount);
        qualityScore.setIssueSummary(issues.toString());
        qualityScore.setCheckedAt(LocalDateTime.now());

        qualityScoreMapper.insert(qualityScore);
        return qualityScore;
    }

    private boolean evaluateRule(QualityRule rule, Map<String, Object> data) {
        String expression = rule.getCheckExpression().trim();
        String type = rule.getRuleType().trim().toLowerCase();
        return switch (type) {
            case "not_null" -> {
                Object value = resolveValue(data, expression);
                yield value != null && (!(value instanceof String text) || !text.isBlank());
            }
            case "regex" -> evaluateRegex(expression, data);
            case "range", "validation", "expression" -> evaluateComparison(expression, data);
            default -> throw new IllegalArgumentException("不支持的质量规则类型: " + rule.getRuleType());
        };
    }

    private boolean evaluateRegex(String expression, Map<String, Object> data) {
        Matcher matcher = REGEX.matcher(expression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("正则规则表达式格式应为: 字段 =~ 正则表达式");
        }
        Object value = resolveValue(data, matcher.group(1));
        String regex = unquote(matcher.group(2).trim());
        return value != null && Pattern.compile(regex).matcher(String.valueOf(value)).matches();
    }

    private boolean evaluateComparison(String expression, Map<String, Object> data) {
        Matcher matcher = COMPARISON.matcher(expression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("质量规则表达式格式应为: 字段 比较符 值");
        }
        Object actual = resolveValue(data, matcher.group(1));
        Object expected = parseLiteral(matcher.group(3).trim());
        String operator = matcher.group(2);
        if ("=".equals(operator) || "==".equals(operator)) {
            return valuesEqual(actual, expected);
        }
        if ("!=".equals(operator)) {
            return !valuesEqual(actual, expected);
        }
        if (actual == null || expected == null) {
            return false;
        }
        int comparison = compare(actual, expected);
        return switch (operator) {
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Map<String, Object> data, String path) {
        Object current = data;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    private Object parseLiteral(String text) {
        String literal = unquote(text);
        if ("null".equalsIgnoreCase(literal)) {
            return null;
        }
        if ("true".equalsIgnoreCase(literal) || "false".equalsIgnoreCase(literal)) {
            return Boolean.valueOf(literal);
        }
        try {
            return new java.math.BigDecimal(literal);
        } catch (NumberFormatException ignored) {
            return literal;
        }
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual instanceof Number && expected instanceof Number) {
            return compare(actual, expected) == 0;
        }
        return Objects.equals(actual, expected)
                || (actual != null && expected != null
                && String.valueOf(actual).equals(String.valueOf(expected)));
    }

    private int compare(Object actual, Object expected) {
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new java.math.BigDecimal(String.valueOf(actual))
                        .compareTo(new java.math.BigDecimal(String.valueOf(expected)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("数值比较的字段和值必须是数字", e);
            }
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    private void validateRule(QualityRule rule) {
        if (rule.getCheckExpression() == null || rule.getCheckExpression().isBlank()) {
            throw new IllegalArgumentException("检查表达式不能为空");
        }
        String type = rule.getRuleType().trim().toLowerCase();
        if (!List.of("not_null", "regex", "range", "validation", "expression").contains(type)) {
            throw new IllegalArgumentException("不支持的质量规则类型: " + rule.getRuleType());
        }
    }

    public List<QualityScore> getScoreHistory(String dataType, Long dataId) {
        LambdaQueryWrapper<QualityScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityScore::getDataType, dataType)
               .eq(QualityScore::getDataId, dataId)
               .orderByDesc(QualityScore::getCheckedAt);
        return qualityScoreMapper.selectList(wrapper);
    }
}
