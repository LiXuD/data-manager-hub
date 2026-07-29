package com.dataplatform.governance.monitor.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.handler.CodeEnumTypeHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRulePersistenceMappingTest {

    @Test
    void statusUsesPersistedEnumCodeHandler() throws NoSuchFieldException {
        TableName tableName = AlertRule.class.getAnnotation(TableName.class);
        TableField statusField = AlertRule.class.getDeclaredField("status").getAnnotation(TableField.class);

        assertThat(tableName.autoResultMap()).isTrue();
        assertThat(statusField.typeHandler()).isEqualTo(CodeEnumTypeHandler.class);
    }
}
