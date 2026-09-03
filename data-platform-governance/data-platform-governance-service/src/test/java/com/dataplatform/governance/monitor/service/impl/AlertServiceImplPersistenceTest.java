package com.dataplatform.governance.monitor.service.impl;

import com.dataplatform.governance.monitor.entity.AlertRecord;
import com.dataplatform.governance.monitor.entity.AlertRule;
import com.dataplatform.governance.monitor.mapper.AlertRecordMapper;
import com.dataplatform.governance.monitor.mapper.AlertRuleMapper;
import com.dataplatform.common.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplPersistenceTest {

    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private AlertRuleMapper alertRuleMapper;

    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertServiceImpl(alertRecordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", alertRuleMapper);
    }

    @Test
    void failsWhenAlertRecordCannotBeInserted() {
        when(alertRecordMapper.insert(any(AlertRecord.class))).thenReturn(0);
        AlertRecord record = new AlertRecord();
        record.setRuleId(1L);
        record.setAlertType("test");
        record.setAlertTitle("test alert");
        record.setLevel("warning");

        assertThrows(IllegalStateException.class, () -> service.saveRecord(record));
    }

    @Test
    void rejectsNullAlertRecord() {
        assertThrows(IllegalArgumentException.class, () -> service.saveRecord(null));
    }

    @Test
    void failsWhenAlertRuleCannotBeInserted() {
        when(alertRuleMapper.insert(any(AlertRule.class))).thenReturn(0);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.saveRule(new AlertRule()));
        }
    }

    @Test
    void failsWhenAlertRuleCannotBeUpdated() {
        AlertRule existing = new AlertRule();
        existing.setId(1L);
        when(alertRuleMapper.selectById(1L)).thenReturn(existing);
        when(alertRuleMapper.updateById(any(AlertRule.class))).thenReturn(0);

        AlertRule update = new AlertRule();
        update.setId(1L);
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.updateRule(update));
        }
    }

    @Test
    void failsWhenAlertRuleCannotBeDeleted() {
        when(alertRuleMapper.selectById(1L)).thenReturn(new AlertRule());
        when(alertRuleMapper.delete(any())).thenReturn(0);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.deleteRule(1L));
        }
    }

    @Test
    void failsWhenAlertRecordCannotBeResolved() {
        when(alertRecordMapper.selectOne(any())).thenReturn(new AlertRecord());
        when(alertRecordMapper.update(any(AlertRecord.class), any())).thenReturn(0);

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(1L);
            userContext.when(() -> UserContext.hasPermission("system:admin")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.resolveRecord(1L, "fixed"));
        }
    }
}
