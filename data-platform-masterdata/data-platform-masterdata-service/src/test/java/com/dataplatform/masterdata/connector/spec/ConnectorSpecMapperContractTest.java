package com.dataplatform.masterdata.connector.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ConnectorSpecMapperContractTest {

    @Test
    void draftSqlIsJsonbSafeAtomicCasAndCannotTouchLegacy() throws Exception {
        String insert = ConnectorSpecDraftMapper.class.getMethod(
                "insertDraft", ConnectorSpecDraftMapper.DraftWrite.class)
                .getAnnotation(Insert.class).value()[0];
        assertTrue(insert.contains("CAST(#{row.pipelineSnapshot} AS jsonb)"));
        assertTrue(insert.contains("CAST(#{row.connectorSpec} AS jsonb)"));
        assertTrue(insert.contains("ON CONFLICT"));

        Method updateMethod = Arrays.stream(ConnectorSpecDraftMapper.class.getMethods())
                .filter(method -> method.getName().equals("updateDraft")).findFirst().orElseThrow();
        String update = updateMethod.getAnnotation(Update.class).value()[0];
        assertTrue(update.contains("authoring_mode = 'SIMPLE_CONNECTOR'"));
        assertTrue(update.contains("draft_version = #{expectedDraftVersion}"));
        assertTrue(update.contains("status = 'DRAFT'"));
        assertTrue(update.contains("snapshot_hash = NULL"));
        assertTrue(update.contains("hash_algorithm = NULL"));
        assertTrue(update.contains("integrity_hash = NULL"));

        Method conversionMethod = Arrays.stream(ConnectorSpecDraftMapper.class.getMethods())
                .filter(method -> method.getName().equals("convertLegacyDraft"))
                .findFirst().orElseThrow();
        String conversion = conversionMethod.getAnnotation(Update.class).value()[0];
        assertTrue(conversion.contains("authoring_mode = 'ADVANCED_LEGACY'"));
        assertTrue(conversion.contains("authoring_mode = 'SIMPLE_CONNECTOR'"));
        assertTrue(conversion.contains("draft_version = #{expectedDraftVersion}"));
        assertTrue(conversion.contains("status = 'DRAFT'"));
        assertTrue(conversion.contains("CAST(#{pipelineSnapshot} AS jsonb)"));
        assertTrue(conversion.contains("CAST(#{connectorSpec} AS jsonb)"));
        assertTrue(conversion.contains("snapshot_hash = NULL"));
        assertTrue(conversion.contains("hash_algorithm = NULL"));
        assertTrue(conversion.contains("integrity_hash = NULL"));
    }

    @Test
    void vendorFactsIncludeThePlatformTimeoutWithoutSecretMaterial() throws Exception {
        String sql = ConnectorSpecFactsMapper.class.getMethod("findVendorFacts", Long.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        assertTrue(sql.contains("vc.timeout"));
        assertFalse(sql.contains("secret_key"));
        assertFalse(sql.contains("config_value"));
    }

    @Test
    void ownershipQueryReturnsNamesOnlyAndNeverSelectsSecretMaterial() throws Exception {
        String sql = ConnectorSpecFactsMapper.class.getMethod("findOwnedSecretRefs", Long.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        assertTrue(sql.startsWith("select ref"));
        assertFalse(sql.contains("select config_value"));
        assertFalse(sql.contains("select secret_key"));
        assertTrue(sql.contains("config_value is not null"));
        assertTrue(sql.contains("secret_key is not null"));
    }

    @Test
    void exactPluginVersionLookupDoesNotDependOnMutableParentCatalogRow() throws Exception {
        String sql = ConnectorSpecFactsMapper.class.getMethod(
                "findPluginVersion", String.class, String.class)
                .getAnnotation(Select.class).value()[0].toLowerCase();
        assertTrue(sql.contains("from connector_plugin_version cpv"));
        assertFalse(sql.contains("join connector_plugin"));
    }

    @Test
    void testFactInsertSelectAtomicallyRechecksEveryDraftFactAndStoresNoPayload() throws Exception {
        String sql = ConnectorSpecLifecycleMapper.class.getMethod(
                "insertTestFact", ConnectorSpecLifecycleMapper.TestFactWrite.class)
                .getAnnotation(Insert.class).value()[0].toLowerCase();
        assertTrue(sql.contains("with matched_draft as materialized"));
        assertTrue(sql.contains("for key share"));
        assertTrue(sql.contains("insert into vendor_connector_test_fact"));
        assertTrue(sql.contains("select draft.vendor_config_id"));
        assertTrue(sql.contains("draft.id = #{fact.draftid}"));
        assertTrue(sql.contains("draft.status = 'draft'"));
        assertTrue(sql.contains("draft.authoring_mode = 'simple_connector'"));
        assertTrue(sql.contains("draft.draft_version = #{fact.draftversion}"));
        assertTrue(sql.contains("draft.spec_hash = #{fact.spechash}"));
        assertTrue(sql.contains("draft.compiler_version = #{fact.compilerversion}"));
        assertTrue(sql.contains("draft.compile_hash = #{fact.compilehash}"));
        assertTrue(sql.contains("draft.security_version = #{fact.securityversion}"));
        assertTrue(sql.contains("draft.connector_spec = cast(#{fact.connectorspec} as jsonb)"));
        assertTrue(sql.contains("draft.pipeline_snapshot = cast(#{fact.pipelinesnapshot} as jsonb)"));
        assertFalse(sql.contains("normalized_data"));
        assertFalse(sql.contains("safe_message"));
        assertFalse(sql.contains("params"));
        assertFalse(sql.contains("raw_response"));
        assertFalse(sql.contains("tested_at"));
    }

    @Test
    void publishLocksControlDraftAndEveryActiveRow() throws Exception {
        String control = select("lockControl", Long.class);
        assertTrue(control.contains("from vendor_config"));
        assertTrue(control.contains("coalesce(deleted, false) = false"));
        assertTrue(control.contains("for update"));
        String draft = select("lockDraft", Long.class);
        assertTrue(draft.contains("status = 'draft'"));
        assertTrue(draft.contains("for update"));
        String active = select("lockActive", Long.class);
        assertTrue(active.contains("status = 'active'"));
        assertTrue(active.contains("order by id"));
        assertTrue(active.contains("for update"));
    }

    @Test
    void publishGateAndInsertBindEveryImmutableSimpleFact() throws Exception {
        String gate = select("hasSuccessfulTestFact", Long.class, Integer.class,
                String.class, String.class, String.class);
        assertTrue(gate.contains("vendor_config_id = #{vendorconfigid}"));
        assertTrue(gate.contains("draft_version = #{draftversion}"));
        assertTrue(gate.contains("spec_hash = #{spechash}"));
        assertTrue(gate.contains("snapshot_hash = #{snapshothash}"));
        assertTrue(gate.contains("compile_hash = #{compilehash}"));
        assertTrue(gate.contains("authoring_mode = 'simple_connector'"));
        assertTrue(gate.contains("test_succeeded = true"));

        Method method = ConnectorSpecPublishMapper.class.getMethod(
                "insertPublished", ConnectorSpecPublishMapper.PublishedWrite.class);
        String insert = method.getAnnotation(Insert.class).value()[0].toLowerCase();
        assertTrue(insert.contains("with matched_draft as materialized"));
        assertTrue(insert.contains("draft.id = #{row.draftid}"));
        assertTrue(insert.contains("draft.status = 'draft'"));
        assertTrue(insert.contains("draft.authoring_mode = 'simple_connector'"));
        assertTrue(insert.contains("draft.draft_version = #{row.expecteddraftversion}"));
        assertTrue(insert.contains("draft.security_version = #{row.securityversion}"));
        assertTrue(insert.contains("draft.spec_hash = #{row.spechash}"));
        assertTrue(insert.contains("draft.compiler_version = #{row.compilerversion}"));
        assertTrue(insert.contains("draft.compile_hash = #{row.compilehash}"));
        assertTrue(insert.contains("draft.connector_spec = cast(#{row.connectorspec} as jsonb)"));
        assertTrue(insert.contains("draft.pipeline_snapshot = cast(#{row.pipelinesnapshot} as jsonb)"));
        assertTrue(insert.contains("draft.snapshot_hash is null"));
        assertTrue(insert.contains("draft.hash_algorithm is null"));
        assertTrue(insert.contains("draft.integrity_hash is null"));
        assertTrue(insert.contains("'v2_embedded'"));
        assertTrue(insert.contains("'simple_connector'"));
        assertTrue(insert.contains("'active'"));
        assertTrue(insert.contains("#{row.previousversionid}"));
        assertTrue(method.getAnnotation(Options.class).useGeneratedKeys());
    }

    @Test
    void publishStatusAndPointerUpdatesAreExactCas() throws Exception {
        String supersede = update("supersedeActive", Long.class, Long.class,
                Long.class, java.time.LocalDateTime.class);
        assertTrue(supersede.contains("status = 'superseded'"));
        assertTrue(supersede.contains("id = #{id}"));
        assertTrue(supersede.contains("vendor_config_id = #{vendorconfigid}"));
        assertTrue(supersede.contains("status = 'active'"));
        String pointer = update("casActivePointer", Long.class, Integer.class,
                Long.class, Long.class, java.time.LocalDateTime.class);
        assertTrue(pointer.contains("runtime_mode = 'plugin'"));
        assertTrue(pointer.contains("connector_version = #{expectedconnectorversion}"));
        assertTrue(pointer.contains("active_connector_version_id is not distinct from #{expectedactiveid}"));
        assertTrue(pointer.contains("active_connector_version_id = #{newactiveid}"));
        assertTrue(pointer.contains("connector_version = #{expectedconnectorversion} + 1"));
        assertFalse(pointer.contains("updated_by"));
    }

    @Test
    void historyAndRollbackSqlLockAndCopyImmutableTargetFacts() throws Exception {
        String history = select("findHistoryVersions", Long.class);
        assertTrue(history.contains("status <> 'draft'"));
        assertTrue(history.contains("order by version_no desc, id desc"));
        assertFalse(history.contains("pipeline_snapshot ->"));

        String target = select("lockTarget", Long.class, Integer.class);
        assertTrue(target.contains("version_no = #{versionno}"));
        assertTrue(target.contains("status in ('active', 'superseded')"));
        assertTrue(target.contains("for update"));

        Method method = ConnectorSpecPublishMapper.class.getMethod(
                "insertRollback", ConnectorSpecPublishMapper.RollbackWrite.class);
        String insert = method.getAnnotation(Insert.class).value()[0].toLowerCase();
        assertTrue(insert.contains("with matched_target as materialized"));
        assertTrue(insert.contains("target.id = #{row.targetid}"));
        assertTrue(insert.contains("target.vendor_config_id = #{row.vendorconfigid}"));
        assertTrue(insert.contains("target.version_no = #{row.targetversionno}"));
        assertTrue(insert.contains("target.status in ('active', 'superseded')"));
        assertTrue(insert.contains("target.pipeline_snapshot = cast(#{row.pipelinesnapshot} as jsonb)"));
        assertTrue(insert.contains("target.snapshot_hash is not distinct from #{row.snapshothash}"));
        assertTrue(insert.contains("target.hash_algorithm is not distinct from #{row.hashalgorithm}"));
        assertTrue(insert.contains("target.integrity_hash is not distinct from #{row.integrityhash}"));
        assertTrue(insert.contains("target.connector_spec is null and #{row.connectorspec} is null"));
        assertTrue(insert.contains("target.connector_spec = cast(#{row.connectorspec} as jsonb)"));
        assertTrue(insert.contains("target.spec_hash is not distinct from #{row.spechash}"));
        assertTrue(insert.contains("target.compiler_version is not distinct from #{row.compilerversion}"));
        assertTrue(insert.contains("target.compile_hash is not distinct from #{row.compilehash}"));
        assertTrue(insert.contains("target.security_version is not distinct from #{row.securityversion}"));
        assertTrue(insert.contains("select #{row.vendorconfigid}, #{row.versionno}, 0"));
        assertTrue(insert.contains("matched_target.pipeline_snapshot"));
        assertTrue(insert.contains("matched_target.connector_spec"));
        assertTrue(insert.contains("'active'"));
        assertTrue(insert.contains("#{row.previousversionid}"));
        assertTrue(method.getAnnotation(Options.class).useGeneratedKeys());
    }

    private String select(String method, Class<?>... parameterTypes) throws Exception {
        return ConnectorSpecPublishMapper.class.getMethod(method, parameterTypes)
                .getAnnotation(Select.class).value()[0].toLowerCase();
    }

    private String update(String method, Class<?>... parameterTypes) throws Exception {
        return ConnectorSpecPublishMapper.class.getMethod(method, parameterTypes)
                .getAnnotation(Update.class).value()[0].toLowerCase();
    }
}
