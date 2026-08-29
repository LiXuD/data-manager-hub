import { describe, expect, it } from 'vitest'
import workspace from '../../../views/interface/components/config/VendorConnectorWorkspace.vue?raw'

describe('connector product workspace', () => {
  it('uses one product schema form and never exposes editable engine topology', () => {
    expect(workspace.match(/<JsonSchemaForm/g)).toHaveLength(1)
    expect(workspace).toContain('v-model="formConfig"')
    expect(workspace).toContain('selectedPluginId')
    expect(workspace).toContain('selectedPluginVersion')
    for (const token of [
      'addStep', 'removeStep', 'moveStep', 'transportCount',
      'v-model="step.stageKey"', 'v-model="step.capability"',
      'v-model="step.order"', 'v-model="step.enabled"'
    ]) expect(workspace).not.toContain(token)
  })

  it('covers product lifecycle routes and keeps Legacy conversion/history available', () => {
    for (const api of [
      'getConnectorSpecCatalog', 'getConnectorSpecDraft', 'getConnectorSpecHistory',
      'saveConnectorSpecDraft', 'validateConnectorSpecDraft',
      'testConnectorSpecDraft', 'publishConnectorSpecDraft', 'rollbackConnectorSpecVersion',
      'previewConnectorSpecConversion', 'convertLegacyConnectorSpec', 'previewConnectorSpecUpgrade'
    ]) expect(workspace).toContain(api)
    expect(workspace).toContain('Legacy 高级流水线草稿')
    expect(workspace).toContain('转换只会更新当前草稿，活动版本和历史版本保持不变')
    expect(workspace).not.toContain('getConnectorExecutionPlan')
    expect(workspace).not.toContain('高级执行计划（只读）')
    expect(workspace).not.toContain('响应字段映射（可选）')
  })

  it('fails closed for viewers without permission and gates every mutation', () => {
    expect(workspace).toContain("allowed('connector-plugin:view')")
    expect(workspace).toContain('title="403"')
    for (const permission of [
      'connector-plugin:bind', 'connector-plugin:test',
      'connector-plugin:publish', 'connector-plugin:rollback'
    ]) expect(workspace).toContain(`allowed('${permission}')`)
    expect(workspace).toContain('error?.response?.status === 403')
    expect(workspace).toContain('accessDenied.value = true')
  })

  it('keeps old raw endpoints as GET-only fallback', () => {
    expect(workspace).toContain('Promise.allSettled')
    expect(workspace).toContain('getActiveVendorConnector')
    expect(workspace).toContain('getVendorConnectorDraft')
    expect(workspace).toContain('getVendorConnectorVersions')
    expect(workspace).not.toContain('saveVendorConnectorDraft')
    expect(workspace).not.toContain('validateVendorConnector')
    expect(workspace).not.toContain('testVendorConnector')
    expect(workspace).not.toContain('publishVendorConnector')
    expect(workspace).not.toContain('rollbackVendorConnector')
  })

  it('keeps engine facts out of the ordinary page while retaining safe digest helpers', () => {
    expect(workspace).not.toContain('transportMode')
    expect(workspace).not.toContain('outputMode')
    expect(workspace).not.toContain("value === 'HOST_MANAGED_MULTI'")
    expect(workspace).toContain('value.slice(0, 12)')
    expect(workspace).not.toContain('shortHash(row.configHash)')
  })

  it('previews fixed-version upgrades without adding a second ordinary configuration surface', () => {
    expect(workspace).toContain('@update:model-value="requestVersionChange"')
    expect(workspace).toContain('固定版本升级预检')
    expect(workspace).toContain('upgradePreview.schemaChanges')
    expect(workspace).toContain('upgradePreview.configChanges')
    expect(workspace).toContain('upgradePreview.planDiff.coordinateChangeCount')
    expect(workspace).toContain('确认使用目标版本')
    expect(workspace).toContain("throw new Error('CONNECTOR_UPGRADE_PREVIEW_EMPTY')")
    expect(workspace).toContain("ElMessage.warning('升级预检失败，已保留当前固定版本')")
    expect(workspace).toContain("pendingPluginVersion.value = ''")
    expect(workspace).not.toContain('响应字段映射（可选）')
    expect(workspace).not.toContain('safeMappingPath(mapping.sourcePath')
  })
})
