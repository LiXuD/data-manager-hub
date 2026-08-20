import { describe, expect, it } from 'vitest'
import connectorWorkspace from '../../../views/interface/components/config/VendorConnectorWorkspace.vue?raw'

describe('advanced connector workspace baseline', () => {
  it('removes editable engine stages from the ordinary product workflow', () => {
    for (const binding of [
      'v-model="step.stageKey"',
      'v-model="step.pluginId"',
      'v-model="step.pluginVersion"',
      'v-model="step.capability"',
      'v-model="step.enabled"',
      '@click="addStep"',
      '@click="moveStep(index, -1)"',
      '@click="moveStep(index, 1)"',
      '@click="removeStep(index)"',
      'TRANSPORT {{ transportCount }}/1'
    ]) {
      expect(connectorWorkspace).not.toContain(binding)
    }
    expect(connectorWorkspace).toContain('选择一个固定插件版本，只填写一次业务配置')
    expect(connectorWorkspace).toContain('v-model="formConfig"')
    expect(connectorWorkspace).toContain('高级执行计划（只读）')
  })

  it('uses product CAS and keeps Legacy and raw pipeline access read-only', () => {
    expect(connectorWorkspace).toContain('saveConnectorSpecDraft')
    expect(connectorWorkspace).toContain('validateConnectorSpecDraft(props.config.id)')
    expect(connectorWorkspace).toContain('testConnectorSpecDraft(props.config.id, params)')
    expect(connectorWorkspace).toContain('publishConnectorSpecDraft(props.config.id, draft.value.draftVersion)')
    expect(connectorWorkspace).toContain('rollbackConnectorSpecVersion')
    expect(connectorWorkspace).toContain('previewConnectorSpecConversion')
    expect(connectorWorkspace).toContain('convertLegacyConnectorSpec')
    expect(connectorWorkspace).toContain('旧高级流水线只读模式')
    expect(connectorWorkspace).toContain('getActiveVendorConnector')
    expect(connectorWorkspace).not.toContain('saveVendorConnectorDraft')
    expect(connectorWorkspace).not.toContain('testVendorConnector')
    expect(connectorWorkspace).not.toContain('publishVendorConnector')
    expect(connectorWorkspace).toContain('不可变发布版本')
  })
})
