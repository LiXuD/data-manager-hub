package com.dataplatform.governance.quality.service;

import com.dataplatform.governance.quality.entity.QualityRule;
import com.dataplatform.governance.quality.mapper.QualityRuleMapper;
import com.dataplatform.governance.quality.mapper.QualityScoreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityServicePersistenceTest {

    @Mock
    private QualityRuleMapper qualityRuleMapper;
    @Mock
    private QualityScoreMapper qualityScoreMapper;

    private QualityService service;

    @BeforeEach
    void setUp() {
        service = new QualityService();
        ReflectionTestUtils.setField(service, "baseMapper", qualityRuleMapper);
        ReflectionTestUtils.setField(service, "qualityScoreMapper", qualityScoreMapper);
    }

    @Test
    void failsWhenQualityScoreCannotBePersisted() {
        QualityRule rule = new QualityRule();
        rule.setRuleType("not_null");
        rule.setCheckExpression("name");
        when(qualityRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(qualityScoreMapper.insert(any(com.dataplatform.governance.quality.entity.QualityScore.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.checkQuality("customer", 1L, Map.of("name", "Alice")));
    }

    @Test
    void failsWhenQualityRuleCannotBePersisted() {
        QualityRule rule = new QualityRule();
        rule.setRuleType("not_null");
        rule.setCheckExpression("name");
        when(qualityRuleMapper.insert(any(QualityRule.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.addRule(rule));
    }
}
