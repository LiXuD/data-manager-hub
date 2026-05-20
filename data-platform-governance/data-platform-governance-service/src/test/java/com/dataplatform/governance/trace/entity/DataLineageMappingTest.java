package com.dataplatform.governance.trace.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.baomidou.mybatisplus.annotation.TableField;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class DataLineageMappingTest {

    @Test
    void mapsApiFieldsToCurrentDataLineageColumns() throws Exception {
        assertColumn("sourceType", "source_type");
        assertColumn("sourceId", "source_id");
        assertColumn("sourceName", "source_name");
        assertColumn("targetType", "target_type");
        assertColumn("targetId", "target_id");
        assertColumn("targetName", "target_name");
        assertColumn("relationType", "relation_type");
        assertColumn("transformRule", "transform_rule");
    }

    private void assertColumn(String fieldName, String columnName) throws Exception {
        Field field = DataLineage.class.getDeclaredField(fieldName);
        TableField tableField = field.getAnnotation(TableField.class);
        assertEquals(columnName, tableField.value());
    }
}
