package com.dataplatform.governance.quality.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dataplatform.common.result.Result;
import com.dataplatform.governance.quality.entity.QualityRule;
import com.dataplatform.governance.quality.service.QualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class QualityControllerValidationTest {

    private QualityService qualityService;
    private QualityController controller;

    @BeforeEach
    void setUp() {
        qualityService = mock(QualityService.class);
        controller = new QualityController();
        ReflectionTestUtils.setField(controller, "qualityService", qualityService);
    }

    @Test
    void rejectsNullRuleBody() {
        ResponseEntity<?> response = controller.addRule(null);

        assertEquals(400, ((Result<?>) response.getBody()).getCode());
        verifyNoInteractions(qualityService);
    }

    @Test
    void mapsRulePersistenceFailureToConflict() {
        QualityRule rule = new QualityRule();
        rule.setRuleName("名称非空");
        rule.setRuleType("not_null");
        rule.setDataType("customer");
        rule.setCheckExpression("name");
        when(qualityService.addRule(any(QualityRule.class)))
                .thenThrow(new IllegalStateException("质量规则保存失败，请重试"));

        ResponseEntity<?> response = controller.addRule(rule);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(409, ((Result<?>) response.getBody()).getCode());
    }
}
