package com.dataplatform.masterdata.connector.spec.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ConnectorLegacyInventoryMapperContractTest {

    @Test
    void mapperIsSelectOnlyAndPagesOneCurrentFactSetPerVendorConfig() throws Exception {
        for (Method method : ConnectorLegacyInventoryMapper.class.getDeclaredMethods()) {
            assertNotNull(method.getAnnotation(Select.class));
            assertNull(method.getAnnotation(Insert.class));
            assertNull(method.getAnnotation(Update.class));
            assertNull(method.getAnnotation(Delete.class));
        }

        String pageSql = ConnectorLegacyInventoryMapper.class.getMethod(
                "findPage", int.class, long.class).getAnnotation(Select.class).value()[0]
                .toLowerCase();
        assertTrue(pageSql.contains("draft.status = 'draft'"));
        assertTrue(pageSql.contains("active.id = vc.active_connector_version_id"));
        assertTrue(pageSql.contains("active.status = 'active'"));
        assertTrue(pageSql.contains("authoring_mode = 'advanced_legacy'"));
        assertTrue(pageSql.contains("active_authoring_mode = 'advanced_legacy' then 0"));
        assertTrue(pageSql.contains("vendor_config_id asc"));
        assertTrue(pageSql.contains("limit #{limit} offset #{offset}"));
        assertFalse(pageSql.contains("secret_key"));
        assertFalse(pageSql.contains("config_value"));
        assertFalse(pageSql.contains("connector_spec"));

        String countSql = ConnectorLegacyInventoryMapper.class.getMethod("countLegacyConfigs")
                .getAnnotation(Select.class).value()[0].toLowerCase();
        assertTrue(countSql.contains("count(distinct vc.id)"));
        assertTrue(countSql.contains("join vendor_info vi"));
        assertTrue(countSql.contains("join data_type dt"));
        assertTrue(countSql.contains("authoring_mode = 'advanced_legacy'"));
        assertFalse(countSql.contains("pipeline_snapshot"));
        assertFalse(countSql.contains("secret_key"));
        assertFalse(countSql.contains("config_value"));
    }
}
