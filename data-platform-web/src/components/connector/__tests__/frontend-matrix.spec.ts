import { describe, expect, it } from 'vitest'
import schemaField from '../JsonSchemaField.vue?raw'
import pluginCenter from '../../../views/connector-plugin/index.vue?raw'
import migrationHistory from '../../../views/connector-migration/index.vue?raw'
import connectorWorkspace from '../../../views/interface/components/config/VendorConnectorWorkspace.vue?raw'
import router from '../../../router/index.ts?raw'

describe('connector frontend acceptance matrix', () => {
  it('renders every supported schema shape with a dedicated control', () => {
    expect(schemaField).toContain("schema.type === 'object' || schema.properties")
    expect(schemaField).toContain("schema.type === 'array'")
    expect(schemaField).toContain('schema.enum')
    expect(schemaField).toContain("schema.type === 'boolean'")
    expect(schemaField).toContain("schema.type === 'integer' || schema.type === 'number'")
    expect(schemaField).toContain("schema['x-ui-widget'] === 'textarea'")
    expect(schemaField).toContain('schemaFieldVisible(child, props.modelValue)')
    expect(schemaField).toContain("child['x-ui-advanced']")
    expect(schemaField).toContain("props.schema['x-platform-managed']")
    expect(schemaField).toContain("groupLabel(group.name)")
  })

  it('only permits selecting an existing secret reference', () => {
    const secretBranch = schemaField.slice(
      schemaField.indexOf('v-if="secretSelector"'),
      schemaField.indexOf('v-else-if="schema.enum"')
    )
    expect(secretBranch).toContain('placeholder="选择密钥引用（不保存明文）"')
    expect(secretBranch).toContain('v-for="item in secretOptions"')
    expect(secretBranch).not.toContain('allow-create')
    expect(secretBranch).not.toContain('el-input')
    expect(schemaField).toContain('secretFieldRepresentation(props.schema, props.fieldName)')
    expect(schemaField).toContain('writeSecretReference(secretRepresentation.value, value)')
  })

  it('pins product draft CAS and confines engine facts to a read-only advanced plan', () => {
    expect(connectorWorkspace).toContain('draft.value.draftVersion')
    expect(connectorWorkspace).toContain('saveConnectorSpecDraft')
    expect(connectorWorkspace).toContain('getConnectorExecutionPlan')
    expect(connectorWorkspace).toContain('高级执行计划（只读）')
    expect(connectorWorkspace).toContain('不返回阶段配置或密钥引用')
    expect(connectorWorkspace).not.toContain('normalizePipelineOrder')
  })

  it('renders readiness and safe errors for every Access instance', () => {
    expect(pluginCenter).toContain(':data="activation?.instances || []"')
    expect(pluginCenter).toContain('prop="serviceInstanceId"')
    expect(pluginCenter).toContain("row.state === 'READY'")
    expect(pluginCenter).toContain("row.safeErrorCode || '—'")
    expect(pluginCenter).toContain('row.safeErrorDigest')
  })

  it('does not bypass backend permissions by role name and handles expected load failures', () => {
    expect(pluginCenter).toContain('const allowed = (permission: string) => userStore.hasPermission(permission)')
    expect(pluginCenter).not.toContain("role.trim().toLowerCase() === 'admin'")
    expect(pluginCenter).toContain("console.warn('加载连接器插件失败', error)")
    expect(migrationHistory).toContain("console.warn('加载厂商连接器迁移历史失败', error)")
    expect(router).toContain("import { getProfile } from '@/api/auth'")
    expect(router).toContain('const syncCurrentUser = async')
  })

  it('guards every mutating plugin and connector action by permission', () => {
    for (const permission of [
      'connector-plugin:import', 'connector-plugin:verify', 'connector-plugin:activate',
      'connector-plugin:disable'
    ]) {
      expect(pluginCenter).toContain(`allowed('${permission}')`)
    }
    for (const permission of [
      'connector-plugin:bind', 'connector-plugin:test',
      'connector-plugin:publish', 'connector-plugin:rollback'
    ]) {
      expect(connectorWorkspace).toContain(`allowed('${permission}')`)
    }
  })
})
