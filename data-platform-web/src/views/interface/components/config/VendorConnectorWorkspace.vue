<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getConfigByVendor } from '@/api/config'
import {
  convertLegacyConnectorSpec,
  getConnectorExecutionPlan,
  getConnectorSpecCatalog,
  getConnectorSpecCatalogVersions,
  getConnectorSpecDraft,
  getConnectorSpecHistory,
  previewConnectorSpecConversion,
  previewConnectorSpecUpgrade,
  publishConnectorSpecDraft,
  rollbackConnectorSpecVersion,
  saveConnectorSpecDraft,
  testConnectorSpecDraft,
  validateConnectorSpecDraft
} from '@/api/connector-spec'
import {
  getActiveVendorConnector,
  getVendorConnectorDraft,
  getVendorConnectorVersions
} from '@/api/vendor-connector'
import JsonEditor from '@/components/common/JsonEditor.vue'
import JsonSchemaForm from '@/components/connector/JsonSchemaForm.vue'
import type {
  ConnectorExecutionPlan,
  ConnectorSpec,
  ConnectorSpecCatalogEntry,
  ConnectorSpecCatalogVersion,
  ConnectorSpecConversionPreview,
  ConnectorSpecDraftView,
  ConnectorSpecHistoryVersion,
  ConnectorSpecResponseMapping,
  ConnectorSpecUpgradePreview,
  ConnectorSpecValidationResult,
  ConnectorTestResult,
  JsonSchemaNode,
  VendorConfigSummary
} from '@/types'
import { mergeSchemaDefaults } from '@/utils/connector'
import { dataTypeDisplayName, vendorDisplayName } from '../../interface-flow'

const props = defineProps<{ modelValue: boolean; config: VendorConfigSummary | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const accessDenied = ref(false)
const rawReadOnlyFallback = ref(false)
const rawSummary = ref({ activeVersion: undefined as number | undefined, draftVersion: undefined as number | undefined, versionCount: 0 })
const catalog = ref<ConnectorSpecCatalogEntry[]>([])
const catalogVersions = ref<ConnectorSpecCatalogVersion[]>([])
const draft = ref<ConnectorSpecDraftView | null>(null)
const history = ref<ConnectorSpecHistoryVersion[]>([])
const plan = ref<ConnectorExecutionPlan | null>(null)
const planVersion = ref<number | undefined>()
const validation = ref<ConnectorSpecValidationResult | null>(null)
const testResult = ref<ConnectorTestResult | null>(null)
const conversionPreview = ref<ConnectorSpecConversionPreview | null>(null)
const upgradePreview = ref<ConnectorSpecUpgradePreview | null>(null)
const pendingPluginVersion = ref('')
const selectedPluginId = ref('')
const selectedPluginVersion = ref('')
const formConfig = ref<Record<string, unknown>>({})
const responseMapping = ref<ConnectorSpecResponseMapping[] | null>(null)
const secretRefs = ref<string[]>(['vendor.secretKey'])
const testVisible = ref(false)
const testParams = ref('{}')
const historyVisible = ref(false)
const upgradeVisible = ref(false)
const advancedPlanExpanded = ref<string[]>([])

const allowed = (permission: string) => userStore.hasPermission(permission)
const isSimple = computed(() => draft.value?.authoringMode === 'SIMPLE_CONNECTOR')
const isLegacy = computed(() => draft.value?.authoringMode === 'ADVANCED_LEGACY')
const activeVersion = computed(() => history.value.find(item => item.status === 'ACTIVE'))
const selectedCatalog = computed(() => catalog.value.find(item => item.pluginId === selectedPluginId.value))
const selectedVersion = computed(() => catalogVersions.value.find(item => item.pluginVersion === selectedPluginVersion.value))
const configSchema = computed<JsonSchemaNode>(() => selectedVersion.value?.configSchema || selectedCatalog.value?.configSchema || { type: 'object', properties: {} })
const canEdit = computed(() => allowed('connector-plugin:bind') && !isLegacy.value && !rawReadOnlyFallback.value)
const canSave = computed(() => canEdit.value && Boolean(selectedPluginId.value && selectedPluginVersion.value))
const hostMapping = computed(() => (selectedVersion.value || selectedCatalog.value)?.outputMode === 'HOST_MAPPING')
const mappingSegment = /^[A-Za-z_][A-Za-z0-9_-]{0,127}$/
const sensitiveMappingSegment = /^(?:auth|authorization|token|secret|password|credential|api[-_]?key)$/i

function cloneJson<T>(value: T): T {
  return value == null ? value : JSON.parse(JSON.stringify(value)) as T
}

function addResponseMapping() {
  if (!canEdit.value || (responseMapping.value?.length || 0) >= 100) return
  responseMapping.value = [...(responseMapping.value || []), {
    targetField: '', sourcePath: '', sourceType: 'field', transformType: 'none'
  }]
}

function removeResponseMapping(index: number) {
  if (!canEdit.value || !responseMapping.value) return
  const next = responseMapping.value.filter((_, itemIndex) => itemIndex !== index)
  responseMapping.value = next.length ? next : null
}

function safeMappingPath(value: string, sourceType = 'field') {
  if (!value || value !== value.trim() || value.length > 256) return false
  const normalized = sourceType === 'jsonPath' && value.startsWith('$.') ? value.slice(2) : value
  if (sourceType === 'jsonPath' && !value.startsWith('$.')) return false
  const segments = normalized.split('.')
  return segments.length > 0 && segments.length <= 32 && segments.every(segment =>
    mappingSegment.test(segment) && !['__proto__', 'prototype', 'constructor'].includes(segment.toLowerCase())
      && !sensitiveMappingSegment.test(segment)
  )
}

function responseMappingValid() {
  if (!hostMapping.value || responseMapping.value == null) return true
  if (!responseMapping.value.length) return false
  const targets = new Set<string>()
  return responseMapping.value.every(mapping =>
    safeMappingPath(mapping.targetField)
      && !mapping.targetField.includes('.')
      && safeMappingPath(mapping.sourcePath, mapping.sourceType || 'field')
      && ['field', 'jsonPath'].includes(mapping.sourceType || 'field')
      && ['none', 'toString', 'toNumber'].includes(mapping.transformType || 'none')
      && !targets.has(mapping.targetField)
      && Boolean(targets.add(mapping.targetField))
  )
}

function resetState() {
  accessDenied.value = false
  rawReadOnlyFallback.value = false
  rawSummary.value = { activeVersion: undefined, draftVersion: undefined, versionCount: 0 }
  catalog.value = []
  catalogVersions.value = []
  draft.value = null
  history.value = []
  plan.value = null
  planVersion.value = undefined
  validation.value = null
  testResult.value = null
  conversionPreview.value = null
  upgradePreview.value = null
  pendingPluginVersion.value = ''
  selectedPluginId.value = ''
  selectedPluginVersion.value = ''
  formConfig.value = {}
  responseMapping.value = null
}

async function load() {
  if (!props.config) return
  resetState()
  if (!allowed('connector-plugin:view')) {
    accessDenied.value = true
    return
  }
  loading.value = true
  try {
    const [catalogResponse, draftResponse, historyResponse] = await Promise.all([
      getConnectorSpecCatalog(props.config.id),
      getConnectorSpecDraft(props.config.id),
      getConnectorSpecHistory(props.config.id)
    ])
    catalog.value = catalogResponse.data?.plugins || []
    draft.value = draftResponse.data
    history.value = historyResponse.data?.versions || []
    if (draft.value?.connectorSpec && draft.value.authoringMode === 'SIMPLE_CONNECTOR') {
      await hydrateSpec(draft.value.connectorSpec)
    } else if (!draft.value?.present && catalog.value.length) {
      await choosePlugin(catalog.value[0].pluginId)
    }
    await Promise.all([loadPlan(), loadSecretRefs()])
  } catch (error: any) {
    if (error?.response?.status === 403) {
      accessDenied.value = true
      return
    }
    console.warn('连接器产品 API 不可用，切换到高级流水线只读视图', error)
    rawReadOnlyFallback.value = true
    await loadRawReadOnlyFallback()
  } finally {
    loading.value = false
  }
}

async function loadRawReadOnlyFallback() {
  if (!props.config) return
  const [activeResult, draftResult, historyResult] = await Promise.allSettled([
    getActiveVendorConnector(props.config.id),
    getVendorConnectorDraft(props.config.id),
    getVendorConnectorVersions(props.config.id)
  ])
  rawSummary.value = {
    activeVersion: activeResult.status === 'fulfilled' ? activeResult.value.data?.versionNo : undefined,
    draftVersion: draftResult.status === 'fulfilled' ? draftResult.value.data?.draftVersion : undefined,
    versionCount: historyResult.status === 'fulfilled' ? (historyResult.value.data || []).length : 0
  }
}

async function loadSecretRefs() {
  if (!props.config?.vendorId) return
  try {
    const response: any = await getConfigByVendor(props.config.vendorId)
    secretRefs.value = ['vendor.secretKey', ...((response.data || [])
      .filter((item: any) => item.isEncrypted)
      .map((item: any) => item.configKey))]
  } catch {
    secretRefs.value = ['vendor.secretKey']
  }
}

async function hydrateSpec(spec: ConnectorSpec) {
  selectedPluginId.value = spec.plugin.pluginId
  await loadCatalogVersions(spec.plugin.pluginId)
  selectedPluginVersion.value = spec.plugin.pluginVersion
  formConfig.value = cloneJson(spec.config || {})
  responseMapping.value = cloneJson(spec.responseMapping || null)
}

async function loadCatalogVersions(pluginId: string) {
  if (!props.config || !pluginId) {
    catalogVersions.value = []
    return
  }
  catalogVersions.value = (await getConnectorSpecCatalogVersions(props.config.id, pluginId)).data || []
}

async function choosePlugin(pluginId: string) {
  selectedPluginId.value = pluginId
  selectedPluginVersion.value = ''
  formConfig.value = {}
  responseMapping.value = null
  await loadCatalogVersions(pluginId)
  const entry = catalog.value.find(item => item.pluginId === pluginId)
  const recommended = catalogVersions.value.find(item => item.pluginVersion === entry?.recommendedVersion)
    || catalogVersions.value.find(item => item.active)
    || catalogVersions.value[0]
  if (recommended) {
    selectedPluginVersion.value = recommended.pluginVersion
    formConfig.value = mergeSchemaDefaults(recommended.configSchema, {}) as Record<string, unknown>
  }
}

function applyVersion(version: string, includeDefaults: boolean) {
  selectedPluginVersion.value = version
  const schema = catalogVersions.value.find(item => item.pluginVersion === version)?.configSchema
  if (schema && includeDefaults) {
    formConfig.value = mergeSchemaDefaults(schema, formConfig.value) as Record<string, unknown>
  }
}

async function requestVersionChange(version: string) {
  if (!props.config || !version || version === selectedPluginVersion.value) return
  const persisted = draft.value?.connectorSpec
  if (!isSimple.value || !draft.value?.present || !draft.value.draftVersion
    || persisted?.plugin.pluginId !== selectedPluginId.value
    || version === persisted?.plugin.pluginVersion) {
    applyVersion(version, true)
    return
  }
  pendingPluginVersion.value = version
  upgradePreview.value = null
  try {
    const preview = (await previewConnectorSpecUpgrade(
      props.config.id,
      draft.value.draftVersion,
      version
    )).data
    if (!preview) throw new Error('CONNECTOR_UPGRADE_PREVIEW_EMPTY')
    upgradePreview.value = preview
    upgradeVisible.value = true
  } catch {
    pendingPluginVersion.value = ''
    upgradePreview.value = null
    upgradeVisible.value = false
    ElMessage.warning('升级预检失败，已保留当前固定版本')
  }
}

function confirmVersionUpgrade() {
  if (!upgradePreview.value?.valid || !pendingPluginVersion.value) return
  applyVersion(pendingPluginVersion.value, false)
  validation.value = null
  upgradeVisible.value = false
}

function currentSpec(): ConnectorSpec {
  if (!selectedPluginId.value || !selectedPluginVersion.value) {
    throw new Error('CONNECTOR_PLUGIN_VERSION_REQUIRED')
  }
  return {
    specVersion: '1',
    plugin: { pluginId: selectedPluginId.value, pluginVersion: selectedPluginVersion.value },
    config: cloneJson(formConfig.value),
    responseMapping: hostMapping.value ? cloneJson(responseMapping.value) : null
  }
}

async function saveDraft() {
  if (!props.config || !canSave.value) return
  if (!responseMappingValid()) {
    ElMessage.error('响应字段映射不合法；请使用安全字段路径且目标字段不得重复')
    return
  }
  saving.value = true
  try {
    draft.value = (await saveConnectorSpecDraft(
      props.config.id,
      draft.value?.present ? draft.value.draftVersion || 0 : 0,
      currentSpec()
    )).data
    validation.value = null
    await loadPlan()
    ElMessage.success('产品配置草稿已保存')
  } finally {
    saving.value = false
  }
}

async function runValidation() {
  if (!props.config || !isSimple.value) return
  validation.value = (await validateConnectorSpecDraft(props.config.id)).data
  if (validation.value.valid) ElMessage.success('连接器产品配置校验通过')
  else ElMessage.error(validation.value.errorCode || '连接器产品配置已漂移，请重新保存')
}

async function runTest() {
  if (!props.config || !isSimple.value) return
  let params: Record<string, unknown>
  try {
    const parsed = JSON.parse(testParams.value || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('invalid')
    params = parsed
  } catch {
    ElMessage.error('测试参数必须是 JSON 对象')
    return
  }
  testResult.value = (await testConnectorSpecDraft(props.config.id, params)).data
  if (testResult.value.success) ElMessage.success('受控测试成功')
  else ElMessage.error(testResult.value.safeMessage || '受控测试失败')
}

async function publishDraft() {
  if (!props.config || !isSimple.value || !draft.value?.draftVersion) return
  await ElMessageBox.confirm('发布后会固化产品配置和执行计划，并切换当前活动版本。是否继续？', '发布连接器', { type: 'warning' })
  await publishConnectorSpecDraft(props.config.id, draft.value.draftVersion)
  ElMessage.success('连接器产品版本已发布')
  emit('changed')
  await load()
}

async function rollback(version: ConnectorSpecHistoryVersion) {
  if (!props.config) return
  await ElMessageBox.confirm(`回滚到 V${version.version} 会复制为新的不可变发布版本。是否继续？`, '回滚确认', { type: 'warning' })
  await rollbackConnectorSpecVersion(props.config.id, version.version, props.config.connectorVersion || 0)
  ElMessage.success('已创建回滚发布版本')
  emit('changed')
  await load()
}

async function loadPlan(version?: number) {
  if (!props.config || (!draft.value?.present && history.value.length === 0)) {
    plan.value = null
    return
  }
  try {
    plan.value = (await getConnectorExecutionPlan(props.config.id, version)).data
    planVersion.value = version
  } catch (error) {
    plan.value = null
    console.warn('加载只读执行计划失败', error)
  }
}

async function previewConversion() {
  if (!props.config || !isLegacy.value) return
  conversionPreview.value = null
  try {
    conversionPreview.value = (await previewConnectorSpecConversion(props.config.id)).data
  } catch (error: any) {
    const safePreview = error?.response?.data?.data as ConnectorSpecConversionPreview | undefined
    if (safePreview) conversionPreview.value = safePreview
    else throw error
  }
}

async function convertLegacy() {
  if (!props.config || !isLegacy.value || !draft.value?.draftVersion || !conversionPreview.value?.convertible) return
  await ElMessageBox.confirm('转换只会更新当前草稿，活动版本和历史版本保持不变；转换后必须重新测试并发布。是否继续？', '转换 Legacy 草稿', { type: 'warning' })
  await convertLegacyConnectorSpec(props.config.id, draft.value.draftVersion)
  ElMessage.success('Legacy 草稿已转换为产品配置，请重新测试并发布')
  await load()
}

function transportLabel(value?: string) {
  return value === 'HOST_MANAGED_MULTI_HTTP' ? '插件管理的多请求' : '平台管理的单次请求'
}

function outputLabel(value?: string) {
  return value === 'PLUGIN_NORMALIZED' ? '插件标准化输出' : '平台响应映射'
}

function shortHash(value?: string) {
  return value ? value.slice(0, 12) : '—'
}

watch(() => props.modelValue, visible => { if (visible) void load() })
</script>

<template>
  <el-drawer :model-value="modelValue" direction="rtl" size="min(1040px, 94vw)" append-to-body @close="emit('update:modelValue', false)">
    <template #header>
      <div class="workspace-header">
        <div><div class="eyebrow">CONNECTOR PRODUCT</div><h3>{{ vendorDisplayName(config?.vendorName) }}</h3><p>{{ dataTypeDisplayName(config?.dataTypeName) }}</p></div>
        <div class="runtime-state"><el-tag type="success">PLUGIN</el-tag><span>配置版本 {{ config?.connectorVersion || 0 }}</span><span>活动版本 V{{ activeVersion?.version || rawSummary.activeVersion || '—' }}</span></div>
      </div>
    </template>

    <div v-loading="loading" class="connector-workspace">
      <el-result v-if="accessDenied" icon="warning" title="403" sub-title="当前账号没有 connector-plugin:view 权限" />

      <template v-else-if="rawReadOnlyFallback">
        <el-alert type="warning" :closable="false" show-icon title="产品配置 API 暂不可用，已进入旧高级流水线只读模式">
          <template #default>不会开放步骤编辑、测试、发布或回滚；请在后端产品 API 恢复后继续。</template>
        </el-alert>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="活动版本">{{ rawSummary.activeVersion ? `V${rawSummary.activeVersion}` : '无' }}</el-descriptions-item>
          <el-descriptions-item label="草稿版本">{{ rawSummary.draftVersion || '无' }}</el-descriptions-item>
          <el-descriptions-item label="历史版本数">{{ rawSummary.versionCount }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <template v-else>
        <el-alert v-if="!config?.activeConnectorVersionId" type="warning" :closable="false" show-icon>
          当前配置尚未发布连接器；完成产品配置、受控测试和发布后才能作为运行路由。
        </el-alert>

        <div class="workspace-toolbar">
          <div>
            <strong>{{ isLegacy ? 'Legacy 高级流水线草稿' : '连接器产品草稿' }}</strong>
            <span>草稿版本 {{ draft?.draftVersion || 0 }}</span>
            <el-tag v-if="draft?.authoringMode" :type="isSimple ? 'success' : 'warning'" size="small">{{ draft.authoringMode }}</el-tag>
          </div>
          <div class="toolbar-actions">
            <el-button v-if="canSave" :loading="saving" @click="saveDraft">保存产品配置</el-button>
            <el-button v-if="isSimple && allowed('connector-plugin:bind')" @click="runValidation">校验</el-button>
            <el-button v-if="isSimple && allowed('connector-plugin:test')" @click="testVisible = true">受控测试</el-button>
            <el-button @click="historyVisible = true">版本历史</el-button>
            <el-button v-if="isSimple && allowed('connector-plugin:publish')" type="primary" @click="publishDraft">发布</el-button>
          </div>
        </div>

        <el-alert v-if="validation" :type="validation.valid ? 'success' : 'error'" :closable="false" show-icon :title="validation.valid ? '校验通过' : '校验失败'">
          <template #default>
            <span v-if="!validation.valid">{{ validation.errorCode || '产品配置已变化，请重新保存' }}</span>
            <span v-else class="hash">编译 {{ validation.compileHash }} · 快照 {{ validation.compiledSnapshotHash }}</span>
          </template>
        </el-alert>

        <section v-if="isLegacy" class="legacy-panel">
          <el-alert type="warning" :closable="false" show-icon title="此草稿由旧高级流水线维护，目前仅可查看和转换">
            <template #default>普通配置不展示或编辑阶段、能力、顺序、启停与传输节点。转换不会修改活动版本或历史记录。</template>
          </el-alert>
          <div class="legacy-actions">
            <el-button v-if="allowed('connector-plugin:bind')" @click="previewConversion">转换预检</el-button>
            <el-button v-if="allowed('connector-plugin:bind') && conversionPreview?.convertible" type="primary" @click="convertLegacy">转换为产品配置</el-button>
          </div>
          <div v-if="conversionPreview" class="conversion-result">
            <el-tag :type="conversionPreview.convertible ? 'success' : 'warning'">{{ conversionPreview.classification }}</el-tag>
            <p v-if="conversionPreview.convertible">可无损转换为 generic-http:2.0.0；转换后仍需重新测试和发布。</p>
            <el-table v-else :data="conversionPreview.reasons" size="small">
              <el-table-column prop="code" label="原因代码" width="260" />
              <el-table-column prop="safeMessage" label="安全说明" />
            </el-table>
          </div>
        </section>

        <section v-else class="product-card">
          <div class="panel-title"><div><strong>插件与配置</strong><p>选择一个固定插件版本，只填写一次业务配置；执行步骤由平台确定性生成。</p></div></div>
          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="连接器插件" required>
                  <el-select :model-value="selectedPluginId" :disabled="!canEdit" filterable style="width:100%" placeholder="请选择连接器插件" @update:model-value="choosePlugin">
                    <el-option v-for="plugin in catalog" :key="plugin.pluginId" :label="`${plugin.displayName} · ${plugin.provider}`" :value="plugin.pluginId" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="固定版本" required>
                  <el-select :model-value="selectedPluginVersion" :disabled="!canEdit || !selectedPluginId" style="width:100%" placeholder="请选择固定版本" @update:model-value="requestVersionChange">
                    <el-option v-for="version in catalogVersions" :key="version.pluginVersion" :label="`${version.pluginVersion} · ${version.status}`" :value="version.pluginVersion" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-descriptions v-if="selectedCatalog" :column="3" size="small" border class="product-facts">
              <el-descriptions-item label="类型">{{ selectedCatalog.connectorKind }}</el-descriptions-item>
              <el-descriptions-item label="请求方式">{{ transportLabel(selectedCatalog.transportMode) }}</el-descriptions-item>
              <el-descriptions-item label="输出治理">{{ outputLabel(selectedCatalog.outputMode) }}</el-descriptions-item>
            </el-descriptions>
            <JsonSchemaForm v-if="selectedPluginVersion" v-model="formConfig" :schema="configSchema" :secret-options="secretRefs" :disabled="!canEdit" />
            <div v-if="selectedPluginVersion && hostMapping" class="response-mapping-editor">
              <div class="mapping-header"><div><strong>响应字段映射（可选）</strong><p>未配置时透传插件解析结果；仅接受安全字段路径，不支持密钥或脚本。</p></div><el-button v-if="canEdit" plain @click="addResponseMapping">添加映射</el-button></div>
              <el-alert v-if="!responseMapping?.length" type="info" :closable="false">当前使用响应透传。</el-alert>
              <div v-for="(mapping, index) in responseMapping || []" :key="index" class="mapping-row">
                <el-input v-model="mapping.targetField" :disabled="!canEdit" placeholder="目标字段，如 companyName" />
                <el-input v-model="mapping.sourcePath" :disabled="!canEdit" placeholder="来源路径，如 data.companyName" />
                <el-select v-model="mapping.sourceType" :disabled="!canEdit"><el-option label="字段路径" value="field" /><el-option label="JSONPath" value="jsonPath" /></el-select>
                <el-select v-model="mapping.transformType" :disabled="!canEdit"><el-option label="不转换" value="none" /><el-option label="转字符串" value="toString" /><el-option label="转数字" value="toNumber" /></el-select>
                <el-button v-if="canEdit" type="danger" link @click="removeResponseMapping(index)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="!selectedPluginVersion" description="选择插件和固定版本后填写配置" />
          </el-form>
        </section>

        <el-collapse v-if="plan" v-model="advancedPlanExpanded" class="advanced-plan">
          <el-collapse-item name="plan">
            <template #title><strong>高级执行计划（只读）</strong><span class="plan-summary">{{ plan.stages.length }} 个平台编译步骤 · {{ planVersion ? `历史 V${planVersion}` : '当前选择' }}</span></template>
            <el-alert type="info" :closable="false">该区域仅供排障，不可编辑，也不返回阶段配置或密钥引用。</el-alert>
            <el-table :data="plan.stages" size="small">
              <el-table-column prop="order" label="顺序" width="70" />
              <el-table-column prop="stageKey" label="阶段标识" min-width="190" />
              <el-table-column prop="capability" label="能力" min-width="150" />
              <el-table-column label="固定插件" min-width="180"><template #default="{ row }">{{ row.pluginId }}@{{ row.pluginVersion }}</template></el-table-column>
              <el-table-column prop="source" label="来源" min-width="170" />
              <el-table-column prop="configHash" label="配置摘要" min-width="180"><template #default="{ row }"><span class="hash">{{ shortHash(row.configHash) }}</span></template></el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </template>
    </div>

    <el-dialog v-model="upgradeVisible" title="固定版本升级预检" width="820px" append-to-body>
      <template v-if="upgradePreview">
        <el-alert :type="upgradePreview.valid ? 'success' : 'error'" :closable="false" show-icon :title="upgradePreview.valid ? '升级预检通过' : (upgradePreview.errorCode || '升级预检失败')">
          <template #default>{{ upgradePreview.safeMessage || '确认后只切换表单中的固定版本，仍需保存、测试和发布。' }}</template>
        </el-alert>
        <el-descriptions :column="2" border class="upgrade-facts">
          <el-descriptions-item label="当前版本">{{ upgradePreview.currentPlugin.pluginId }}@{{ upgradePreview.currentPlugin.pluginVersion }}</el-descriptions-item>
          <el-descriptions-item label="目标版本">{{ upgradePreview.targetPlugin.pluginId }}@{{ upgradePreview.targetPlugin.pluginVersion }}</el-descriptions-item>
          <el-descriptions-item label="预览 Spec">{{ shortHash(upgradePreview.previewSpecHash) }}</el-descriptions-item>
          <el-descriptions-item label="预览快照">{{ shortHash(upgradePreview.compiledSnapshotHash) }}</el-descriptions-item>
        </el-descriptions>
        <h4>Schema 变化</h4>
        <el-table :data="upgradePreview.schemaChanges" size="small" max-height="180">
          <el-table-column prop="path" label="字段路径" />
          <el-table-column prop="changeKind" label="变化" width="130" />
          <el-table-column label="类型" width="180"><template #default="{ row }">{{ row.currentType || '—' }} → {{ row.targetType || '—' }}</template></el-table-column>
          <el-table-column label="密钥引用字段" width="120"><template #default="{ row }">{{ row.secretRef ? '是' : '否' }}</template></el-table-column>
        </el-table>
        <h4>现有配置适配</h4>
        <el-table :data="upgradePreview.configChanges" size="small" max-height="180">
          <el-table-column prop="path" label="字段路径" />
          <el-table-column prop="changeKind" label="变化" width="130" />
          <el-table-column label="目标必填" width="100"><template #default="{ row }">{{ row.targetRequired ? '是' : '否' }}</template></el-table-column>
        </el-table>
        <div class="plan-diff-summary">计划变化：新增 {{ upgradePreview.planDiff.addedStageCount }}、移除 {{ upgradePreview.planDiff.removedStageCount }}、坐标 {{ upgradePreview.planDiff.coordinateChangeCount }}、配置摘要 {{ upgradePreview.planDiff.configHashChangeCount }}、制品摘要 {{ upgradePreview.planDiff.artifactDigestChangeCount }}</div>
      </template>
      <template #footer><el-button @click="upgradeVisible = false">取消</el-button><el-button v-if="upgradePreview?.valid" type="primary" @click="confirmVersionUpgrade">确认使用目标版本</el-button></template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="连接器受控测试" width="720px" append-to-body>
      <el-alert type="warning" :closable="false" show-icon>测试可能真实请求厂商；仅展示安全消息、标准化结果和耗时。</el-alert>
      <div class="test-editor"><label>标准请求参数</label><JsonEditor v-model="testParams" :rows="7" /></div>
      <template v-if="testResult">
        <el-descriptions :column="2" border><el-descriptions-item label="结果"><el-tag :type="testResult.success ? 'success' : 'danger'">{{ testResult.success ? '成功' : '失败' }}</el-tag></el-descriptions-item><el-descriptions-item label="错误分类">{{ testResult.errorCategory || '—' }}</el-descriptions-item><el-descriptions-item label="安全消息" :span="2">{{ testResult.safeMessage || '—' }}</el-descriptions-item></el-descriptions>
        <pre class="test-output">{{ JSON.stringify(testResult.normalizedData, null, 2) }}</pre>
        <el-table :data="testResult.stageTimings"><el-table-column type="index" label="#" width="60" /><el-table-column label="执行插件"><template #default="{ row }">{{ row.pluginId }}@{{ row.pluginVersion }}</template></el-table-column><el-table-column prop="durationMs" label="耗时(ms)" /></el-table>
      </template>
      <template #footer><el-button @click="testVisible = false">关闭</el-button><el-button type="primary" @click="runTest">执行测试</el-button></template>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="不可变发布版本" size="860px" append-to-body>
      <el-table :data="history">
        <el-table-column prop="version" label="版本" width="80"><template #default="{ row }">V{{ row.version }}</template></el-table-column>
        <el-table-column prop="authoringMode" label="创作模式" min-width="150" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="snapshotHash" label="快照摘要" min-width="220"><template #default="{ row }"><span class="hash">{{ row.snapshotHash || 'Legacy 原始摘要' }}</span></template></el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" min-width="180" />
        <el-table-column label="操作" width="190"><template #default="{ row }"><el-button link @click="loadPlan(row.version)">查看计划</el-button><el-button v-if="allowed('connector-plugin:rollback') && row.status !== 'ACTIVE'" type="warning" link @click="rollback(row)">回滚</el-button></template></el-table-column>
      </el-table>
    </el-drawer>
  </el-drawer>
</template>

<style scoped>
.workspace-header { width:100%; display:flex; justify-content:space-between; align-items:flex-end; }.workspace-header h3 { margin:3px 0; font-size:20px; }.workspace-header p { margin:0; color:var(--color-text-secondary); }.eyebrow { color:#00a896; font:600 10px var(--font-mono); letter-spacing:.14em; }.runtime-state { display:flex; gap:12px; align-items:center; color:var(--color-text-secondary); font-size:12px; }
.connector-workspace { display:grid; gap:16px; }.workspace-toolbar { position:sticky; top:0; z-index:2; display:flex; justify-content:space-between; align-items:center; gap:12px; padding:14px; border:1px solid var(--color-border); border-radius:10px; background:var(--color-bg-card); }.workspace-toolbar>div { display:flex; align-items:center; gap:10px; }.workspace-toolbar span { color:var(--color-text-secondary); font-size:12px; }.toolbar-actions { flex-wrap:wrap; justify-content:flex-end; }
.product-card,.legacy-panel,.advanced-plan { border:1px solid var(--color-border); border-radius:12px; padding:16px; background:var(--color-bg-card); }.panel-title { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:16px; }.panel-title p { margin:5px 0 0; color:var(--color-text-secondary); font-size:12px; }.product-facts { margin-bottom:18px; }.legacy-panel,.conversion-result { display:grid; gap:14px; }.legacy-actions { display:flex; gap:8px; }.plan-summary { margin-left:12px; color:var(--color-text-secondary); font-size:12px; font-weight:400; }
.response-mapping-editor { display:grid; gap:10px; margin-top:18px; padding-top:16px; border-top:1px solid var(--color-border); }.mapping-header { display:flex; justify-content:space-between; gap:12px; }.mapping-header p { margin:4px 0 0; color:var(--color-text-secondary); font-size:12px; }.mapping-row { display:grid; grid-template-columns:1fr 1.3fr 120px 120px auto; gap:8px; align-items:center; }.upgrade-facts { margin:14px 0; }.plan-diff-summary { margin-top:14px; padding:10px; border-radius:8px; background:var(--color-bg-light); color:var(--color-text-secondary); }
.hash { font:11px var(--font-mono); color:var(--color-text-secondary); word-break:break-all; }.test-editor { margin:16px 0; }.test-editor label { display:block; margin-bottom:8px; font-weight:600; }.test-output { max-height:240px; overflow:auto; background:var(--color-bg-light); color:var(--color-text-primary); border-radius:8px; padding:12px; }
@media(max-width:900px){.workspace-header,.workspace-toolbar,.mapping-header { align-items:flex-start; flex-direction:column; }.toolbar-actions { justify-content:flex-start; }.runtime-state { flex-wrap:wrap; }.mapping-row { grid-template-columns:1fr 1fr; }}
</style>
