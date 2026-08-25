package com.dataplatform.masterdata.graylog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dataplatform.common.handler.CodeEnumTypeHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrayRulePersistenceMappingTest {

    @Test
    void statusUsesPersistedEnumCodeHandler() throws NoSuchFieldException {
        TableName tableName = GrayRule.class.getAnnotation(TableName.class);
        TableField statusField = GrayRule.class.getDeclaredField("status").getAnnotation(TableField.class);

        assertThat(tableName.autoResultMap()).isTrue();
        assertThat(statusField.typeHandler()).isEqualTo(CodeEnumTypeHandler.class);
    }
}
