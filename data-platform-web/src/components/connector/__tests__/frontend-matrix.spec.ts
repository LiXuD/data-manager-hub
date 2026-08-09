import { describe, expect, it } from 'vitest'
import schemaField from '../JsonSchemaField.vue?raw'
import pluginCenter from '../../../views/connector-plugin/index.vue?raw'
import connectorWorkspace from '../../../views/interface/components/config/VendorConnectorWorkspace.vue?raw'

describe('connector frontend acceptance matrix', () => {
  it('renders every supported schema shape with a dedicated control', () => {
    expect(schemaField).toContain("schema.type === 'object' || schema.properties")
    expect(schemaField).toContain("schema.type === 'array'")
    expect(schemaField).toContain('schema.enum')
    expect(schemaField).toContain("schema.type === 'boolean'")
    expect(schemaField).toContain("schema.type === 'integer' || schema.type === 'number'")
    expect(schemaField).toContain("schema['x-ui-widget'] === 'textarea'")
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

  it('pins draft CAS and exposes an exact activity-to-draft diff', () => {
    expect(connectorWorkspace).toContain('draft.value.draftVersion')
    expect(connectorWorkspace).toContain('saveVendorConnectorDraft')
    expect(connectorWorkspace).toContain('diffConnectorPipelines(active.value?.pipelineSnapshot')
    expect(connectorWorkspace).toContain('活动版本 → 当前草稿差异')
  })

  it('renders readiness and safe errors for every Access instance', () => {
    expect(pluginCenter).toContain(':data="activation?.instances || []"')
    expect(pluginCenter).toContain('prop="serviceInstanceId"')
    expect(pluginCenter).toContain("row.state === 'READY'")
    expect(pluginCenter).toContain("row.safeErrorCode || '—'")
    expect(pluginCenter).toContain('row.safeErrorDigest')
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
