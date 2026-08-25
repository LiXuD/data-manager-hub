package com.dataplatform.access.caller.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyInterfaceMappingTest {

    @Test
    void reusableGrantCanClearNullableLifecycleFields() throws Exception {
        for (String fieldName : List.of(
                "applicationItemId", "approvedCacheDays", "expireAt",
                "revokedAt", "revokedBy", "revokeReason")) {
            Field field = ApiKeyInterface.class.getDeclaredField(fieldName);
            TableField mapping = field.getAnnotation(TableField.class);

            assertEquals(FieldStrategy.ALWAYS, mapping.updateStrategy(), fieldName);
        }
    }
}
