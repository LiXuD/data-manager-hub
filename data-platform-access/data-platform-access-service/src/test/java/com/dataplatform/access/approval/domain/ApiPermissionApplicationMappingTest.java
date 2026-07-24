package com.dataplatform.access.approval.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiPermissionApplicationMappingTest {

    @Test
    void currentTaskProjectionCanBeClearedWithNullValues() throws Exception {
        for (String fieldName : List.of(
                "currentTaskId", "currentTaskKey", "currentTaskName", "currentTaskCreatedAt")) {
            assertAlwaysUpdated(fieldName);
        }
    }

    @Test
    void optionalDraftAndDecisionFieldsCanBeClearedWithNullValues() throws Exception {
        for (String fieldName : List.of("ticketNo", "requestedExpireAt", "approvedExpireAt")) {
            assertAlwaysUpdated(fieldName);
        }
    }

    private void assertAlwaysUpdated(String fieldName) throws Exception {
            Field field = ApiPermissionApplication.class.getDeclaredField(fieldName);
            TableField mapping = field.getAnnotation(TableField.class);

            assertEquals(FieldStrategy.ALWAYS, mapping.updateStrategy(), fieldName);
    }
}
