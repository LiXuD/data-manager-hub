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

/**
 * 观测治理域数据质量的 Quality Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class QualityService extends ServiceImpl<QualityRuleMapper, QualityRule> {

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
        rule.setIsActive(true);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return this.save(rule);
    }

    public QualityScore checkQuality(String dataType, Long dataId) {
        List<QualityRule> rules = getActiveRules(dataType);

        int passCount = 0;
        int failCount = 0;
        StringBuilder issues = new StringBuilder();

        for (QualityRule rule : rules) {
            boolean passed = evaluateRule(rule);
            if (passed) {
                passCount++;
            } else {
                failCount++;
                if (issues.length() > 0) issues.append("; ");
                issues.append(rule.getRuleName());
            }
        }

        double score = rules.isEmpty() ? 100.0 : (double) passCount / rules.size() * 100;

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

    private boolean evaluateRule(QualityRule rule) {
        // 简化实现：实际应根据 checkExpression 动态计算
        String ruleType = rule.getRuleType();
        if ("not_null".equals(ruleType)) {
            return true;
        } else if ("range".equals(ruleType)) {
            return true;
        }
        return true;
    }

    public List<QualityScore> getScoreHistory(String dataType, Long dataId) {
        LambdaQueryWrapper<QualityScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityScore::getDataType, dataType)
               .eq(QualityScore::getDataId, dataId)
               .orderByDesc(QualityScore::getCheckedAt);
        return qualityScoreMapper.selectList(wrapper);
    }
}