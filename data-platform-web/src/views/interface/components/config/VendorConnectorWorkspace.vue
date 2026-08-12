<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getConfigByVendor } from '@/api/config'
import { getConnectorPlugins, getConnectorPluginVersions } from '@/api/connector-plugin'
import {
  getActiveVendorConnector,
  getVendorConnectorDraft,
  getVendorConnectorVersions,
  publishVendorConnector,
  rollbackVendorConnector,
  saveVendorConnectorDraft,
  testVendorConnector,
  validateVendorConnector
} from '@/api/vendor-connector'
import JsonEditor from '@/components/common/JsonEditor.vue'
import JsonSchemaForm from '@/components/connector/JsonSchemaForm.vue'
import type {
  ConnectorCapability,
  ConnectorPipelineStep,
  ConnectorPlugin,
  ConnectorPluginVersion,
  ConnectorTestResult,
  ConnectorValidationResult,
  JsonSchemaNode,
  VendorConnectorDraft,
  VendorConnectorVersion,
  VendorConfigSummary
} from '@/types'
import { diffConnectorPipelines, mergeSchemaDefaults, normalizePipelineOrder, parseJsonDocument } from '@/utils/connector'
import { dataTypeDisplayName, vendorDisplayName } from '../../interface-flow'

const props = defineProps<{ modelValue: boolean; config: VendorConfigSummary | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const active = ref<VendorConnectorVersion | null>(null)
const draft = ref<VendorConnectorDraft | null>(null)
const history = ref<VendorConnectorVersion[]>([])
const plugins = ref<ConnectorPlugin[]>([])
const versionsByPlugin = reactive<Record<string, ConnectorPluginVersion[]>>({})
const secretRefs = ref<string[]>(['vendor.secretKey'])
const validation = ref<ConnectorValidationResult | null>(null)
const testResult = ref<ConnectorTestResult | null>(null)
const testVisible = ref(false)
const testParams = ref('{}')
const historyVisible = ref(false)
const comparedVersion = ref<VendorConnectorVersion | null>(null)

const allowed = (permission: string) => userStore.hasPermission(permission)
const steps = computed(() => draft.value?.pipelineSnapshot || [])
const diff = computed(() => diffConnectorPipelines(active.value?.pipelineSnapshot || [], comparedVersion.value?.pipelineSnapshot || steps.value).filter(item => item.change !== 'UNCHANGED'))
const transportCount = computed(() => steps.value.filter(step => step.enabled && step.capability === 'TRANSPORT').length)

function capabilityLabel(value: ConnectorCapability) {
  return ({ REQUEST_BUILDER: '请求构建', REQUEST_PROCESSOR: '请求处理', TRANSPORT: '传输', RESPONSE_PROCESSOR: '响应处理', RESPONSE_PARSER: '响应解析', RESPONSE_NORMALIZER: '响应归一化' } as Record<ConnectorCapability, string>)[value]
}

async function load() {
  if (!props.config) return
  loading.value = true
  validation.value = null
  try {
    const [activeRes, draftRes, historyRes, pluginRes] = await Promise.all([
      getActiveVendorConnector(props.config.id),
      getVendorConnectorDraft(props.config.id),
      getVendorConnectorVersions(props.config.id),
      getConnectorPlugins()
    ])
    active.value = activeRes.data
    draft.value = draftRes.data
    history.value = historyRes.data || []
    plugins.value = pluginRes.data || []
    await Promise.all([...new Set(steps.value.map(step => step.pluginId).filter(Boolean))].map(loadPluginVersions))
    await loadSecretRefs()
  } finally {
    loading.value = false
  }
}

async function loadSecretRefs() {
  if (!props.config?.vendorId) return
  try {
    const response: any = await getConfigByVendor(props.config.vendorId)
    secretRefs.value = ['vendor.secretKey', ...((response.data || []).filter((item: any) => item.isEncrypted).map((item: any) => item.configKey))]
  } catch {
    secretRefs.value = ['vendor.secretKey']
  }
}

async function loadPluginVersions(pluginId: string) {
  if (!pluginId || versionsByPlugin[pluginId]) return
  versionsByPlugin[pluginId] = (await getConnectorPluginVersions(pluginId)).data || []
}

function addStep() {
  if (!draft.value) return
  const order = draft.value.pipelineSnapshot.length
  draft.value.pipelineSnapshot.push({ stageKey: `stage-${order + 1}`, capability: 'REQUEST_PROCESSOR', pluginId: '', pluginVersion: '', order, enabled: true, config: {} })
  validation.value = null
}

function removeStep(index: number) {
  if (!draft.value) return
  draft.value.pipelineSnapshot = normalizePipelineOrder(draft.value.pipelineSnapshot.filter((_, itemIndex) => itemIndex !== index))
  validation.value = null
}

function moveStep(index: number, direction: -1 | 1) {
  if (!draft.value) return
  const target = index + direction
  if (target < 0 || target >= draft.value.pipelineSnapshot.length) return
  const next = [...draft.value.pipelineSnapshot]
  ;[next[index], next[target]] = [next[target], next[index]]
  draft.value.pipelineSnapshot = normalizePipelineOrder(next)
}

async function pluginChanged(step: ConnectorPipelineStep) {
  step.pluginVersion = ''
  step.config = {}
  await loadPluginVersions(step.pluginId)
}

function versionChanged(step: ConnectorPipelineStep) {
  const version = selectedVersion(step)
  if (!version) return
  if (!version.capabilities.includes(step.capability)) step.capability = version.capabilities[0]
  step.config = mergeSchemaDefaults(schemaFor(step), step.config) as Record<string, unknown>
}

function selectedVersion(step: ConnectorPipelineStep) {
  return (versionsByPlugin[step.pluginId] || []).find(item => item.version === step.pluginVersion)
}

function schemaFor(step: ConnectorPipelineStep): JsonSchemaNode {
  return parseJsonDocument<JsonSchemaNode>(selectedVersion(step)?.configSchemaJson, { type: 'object', properties: {} })
}

async function saveDraft() {
  if (!props.config || !draft.value) return
  saving.value = true
  try {
    const response = await saveVendorConnectorDraft(props.config.id, draft.value.draftVersion, normalizePipelineOrder(draft.value.pipelineSnapshot))
    draft.value = response.data
    validation.value = null
    ElMessage.success('草稿已保存')
  } finally { saving.value = false }
}

async function runValidation() {
  if (!props.config) return
  validation.value = (await validateVendorConnector(props.config.id)).data
  if (validation.value.valid) ElMessage.success('连接器草稿校验通过')
  else ElMessage.error(`发现 ${validation.value.errors.length} 个错误`)
}

async function runTest() {
  if (!props.config) return
  let params: Record<string, unknown>
  try { params = JSON.parse(testParams.value || '{}') } catch { ElMessage.error('测试参数不是合法 JSON'); return }
  testResult.value = (await testVendorConnector(props.config.id, params)).data
  if (testResult.value.success) ElMessage.success('受控测试成功')
  else ElMessage.error(testResult.value.safeMessage || '受控测试失败')
}

async function publish() {
  if (!props.config || !draft.value) return
  await ElMessageBox.confirm('发布后流水线步骤、插件版本和配置快照均不可修改，并会切换运行模式为 PLUGIN。是否继续？', '发布连接器', { type: 'warning' })
  active.value = (await publishVendorConnector(props.config.id, draft.value.draftVersion)).data
  ElMessage.success('连接器版本已发布')
  emit('changed')
  await load()
}

async function rollback(version: VendorConnectorVersion) {
  if (!props.config) return
  await ElMessageBox.confirm(`回滚到版本 V${version.versionNo} 会创建新的发布版本，不修改历史记录。是否继续？`, '回滚确认', { type: 'warning' })
  await rollbackVendorConnector(props.config.id, version.versionNo, props.config.connectorVersion || 0)
  ElMessage.success('已创建回滚发布版本')
  emit('changed')
  await load()
}

watch(() => props.modelValue, visible => { if (visible) void load() })
</script>

<template>
  <el-drawer :model-value="modelValue" direction="rtl" size="min(1100px, 94vw)" append-to-body @close="emit('update:modelValue', false)">
    <template #header>
      <div class="workspace-header">
        <div><div class="eyebrow">VERSIONED CONNECTOR</div><h3>{{ vendorDisplayName(config?.vendorName) }}</h3><p>{{ dataTypeDisplayName(config?.dataTypeName) }}</p></div>
        <div class="runtime-state"><el-tag type="success">PLUGIN</el-tag><span>配置版本 {{ config?.connectorVersion || 0 }}</span><span>活动流水线 V{{ active?.versionNo || '—' }}</span></div>
      </div>
    </template>

    <div v-loading="loading" class="connector-workspace">
      <el-alert v-if="!config?.activeConnectorVersionId" type="warning" :closable="false" show-icon>当前配置尚未发布连接器版本；发布完成前保持停用，现有 OpenAPI 契约不变。</el-alert>
      <div class="workspace-toolbar">
        <div><strong>流水线草稿</strong><span>草稿版本 {{ draft?.draftVersion || 0 }}</span><el-tag :type="transportCount === 1 ? 'success' : 'danger'" size="small">TRANSPORT {{ transportCount }}/1</el-tag></div>
        <div class="toolbar-actions">
          <el-button v-if="allowed('connector-plugin:bind')" @click="addStep">添加步骤</el-button>
          <el-button v-if="allowed('connector-plugin:bind')" :loading="saving" @click="saveDraft">保存草稿</el-button>
          <el-button v-if="allowed('connector-plugin:bind')" @click="runValidation">校验</el-button>
          <el-button v-if="allowed('connector-plugin:test')" @click="testVisible = true">受控测试</el-button>
          <el-button @click="historyVisible = true">版本历史</el-button>
          <el-button v-if="allowed('connector-plugin:publish')" type="primary" @click="publish">发布</el-button>
        </div>
      </div>

      <div v-if="validation" class="validation-panel">
        <el-alert :type="validation.valid ? 'success' : 'error'" :closable="false" show-icon :title="validation.valid ? '校验通过' : '校验失败'">
          <template #default><div v-for="error in validation.errors" :key="error">错误：{{ error }}</div><div v-for="warning in validation.warnings" :key="warning">警告：{{ warning }}</div><div v-if="validation.snapshotHash" class="hash">快照 {{ validation.snapshotHash }}</div></template>
        </el-alert>
      </div>

      <div v-if="!steps.length" class="empty-pipeline"><el-empty description="草稿没有步骤；发布前必须恰好包含一个 TRANSPORT" /></div>
      <div v-else class="pipeline">
        <article v-for="(step, index) in steps" :key="`${step.stageKey}-${index}`" class="stage-card">
          <div class="stage-order">{{ index + 1 }}</div>
          <div class="stage-main">
            <div class="stage-row">
              <el-input v-model="step.stageKey" placeholder="唯一 stageKey" />
              <el-select v-model="step.pluginId" filterable placeholder="插件" @change="pluginChanged(step)"><el-option v-for="plugin in plugins" :key="plugin.pluginId" :label="`${plugin.displayName} (${plugin.pluginId})`" :value="plugin.pluginId" /></el-select>
              <el-select v-model="step.pluginVersion" placeholder="固定版本" @change="versionChanged(step)"><el-option v-for="version in versionsByPlugin[step.pluginId] || []" :key="version.version" :label="`${version.version} · ${version.status}`" :value="version.version" :disabled="version.status === 'DISABLED'" /></el-select>
              <el-select v-model="step.capability" placeholder="能力"><el-option v-for="capability in selectedVersion(step)?.capabilities || []" :key="capability" :label="capabilityLabel(capability)" :value="capability" /></el-select>
              <el-switch v-model="step.enabled" active-text="启用" />
            </div>
            <div v-if="selectedVersion(step)" class="stage-source"><span>{{ step.pluginId }}@{{ step.pluginVersion }}</span><span>{{ step.capability }}</span><span v-if="step.configHash" class="hash">{{ step.configHash }}</span></div>
            <JsonSchemaForm v-if="selectedVersion(step)" v-model="step.config" :schema="schemaFor(step)" :secret-options="secretRefs" />
          </div>
          <div class="stage-actions"><el-button link :disabled="index === 0" @click="moveStep(index, -1)">上移</el-button><el-button link :disabled="index === steps.length - 1" @click="moveStep(index, 1)">下移</el-button><el-button type="danger" link @click="removeStep(index)">删除</el-button></div>
        </article>
      </div>

      <section class="diff-panel"><div class="panel-title"><strong>活动版本 → 当前草稿差异</strong><span>{{ diff.length }} 项变化</span></div><el-table :data="diff" size="small" empty-text="无差异"><el-table-column prop="stageKey" label="步骤" /><el-table-column label="变化"><template #default="{ row }"><el-tag :type="row.change === 'ADDED' ? 'success' : row.change === 'REMOVED' ? 'danger' : 'warning'">{{ row.change }}</el-tag></template></el-table-column><el-table-column label="版本变化"><template #default="{ row }">{{ row.before?.pluginVersion || '—' }} → {{ row.after?.pluginVersion || '—' }}</template></el-table-column><el-table-column label="能力变化"><template #default="{ row }">{{ row.before?.capability || '—' }} → {{ row.after?.capability || '—' }}</template></el-table-column></el-table></section>
    </div>

    <el-dialog v-model="testVisible" title="连接器受控测试" width="760px" append-to-body>
      <el-alert type="warning" :closable="false" show-icon>测试可能真实请求厂商；结果只展示安全消息、标准化响应和阶段耗时。</el-alert>
      <div class="test-editor"><label>标准请求参数</label><JsonEditor v-model="testParams" :rows="7" /></div>
      <template v-if="testResult"><el-descriptions :column="2" border><el-descriptions-item label="结果"><el-tag :type="testResult.success ? 'success' : 'danger'">{{ testResult.success ? '成功' : '失败' }}</el-tag></el-descriptions-item><el-descriptions-item label="错误分类">{{ testResult.errorCategory || '—' }}</el-descriptions-item><el-descriptions-item label="安全消息" :span="2">{{ testResult.safeMessage || '—' }}</el-descriptions-item></el-descriptions><pre class="test-output">{{ JSON.stringify(testResult.normalizedData, null, 2) }}</pre><el-table :data="testResult.stageTimings"><el-table-column prop="stageKey" label="步骤" /><el-table-column prop="capability" label="能力" /><el-table-column prop="durationMs" label="耗时(ms)" /></el-table></template>
      <template #footer><el-button @click="testVisible = false">关闭</el-button><el-button type="primary" @click="runTest">执行测试</el-button></template>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="不可变发布版本" size="820px" append-to-body>
      <el-table :data="history"><el-table-column prop="versionNo" label="版本" width="80"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="snapshotHash" label="快照"><template #default="{ row }"><span class="hash">{{ row.snapshotHash }}</span></template></el-table-column><el-table-column prop="publishedAt" label="发布时间" width="180" /><el-table-column label="操作" width="150"><template #default="{ row }"><el-button link @click="comparedVersion = row">对比</el-button><el-button v-if="allowed('connector-plugin:rollback')" type="warning" link @click="rollback(row)">回滚</el-button></template></el-table-column></el-table>
      <div v-if="comparedVersion" class="history-diff"><div class="panel-title"><strong>活动版本 → V{{ comparedVersion.versionNo }}</strong><el-button link @click="comparedVersion = null">清除</el-button></div><el-table :data="diff"><el-table-column prop="stageKey" label="步骤" /><el-table-column prop="change" label="变化" /><el-table-column label="插件"><template #default="{ row }">{{ row.after?.pluginId || row.before?.pluginId }}@{{ row.after?.pluginVersion || '—' }}</template></el-table-column></el-table></div>
    </el-drawer>
  </el-drawer>
</template>

<style scoped>
.workspace-header { width:100%; display:flex; justify-content:space-between; align-items:flex-end; }.workspace-header h3 { margin:3px 0; font-size:20px; }.workspace-header p { margin:0; color:var(--color-text-secondary); }.eyebrow { color:#00a896; font:600 10px var(--font-mono); letter-spacing:.14em; }.runtime-state { display:flex; gap:12px; align-items:center; color:var(--color-text-secondary); font-size:12px; }
.connector-workspace { display:grid; gap:16px; }.workspace-toolbar { position:sticky; top:0; z-index:2; display:flex; justify-content:space-between; align-items:center; gap:12px; padding:14px; border:1px solid var(--color-border); border-radius:10px; background:var(--color-bg-card); }.workspace-toolbar>div { display:flex; align-items:center; gap:10px; }.workspace-toolbar span { color:var(--color-text-secondary); font-size:12px; }.toolbar-actions { flex-wrap:wrap; justify-content:flex-end; }
.pipeline { display:grid; gap:12px; }.stage-card { display:grid; grid-template-columns:36px minmax(0,1fr) 56px; gap:12px; border:1px solid var(--color-border); background:var(--color-bg-card); border-radius:12px; padding:14px; }.stage-order { width:30px; height:30px; display:grid; place-items:center; border-radius:50%; background:rgba(0,184,148,.13); color:#00a896; font-weight:700; }.stage-row { display:grid; grid-template-columns:1.1fr 1.5fr .8fr 1.1fr auto; gap:8px; }.stage-source { display:flex; gap:12px; margin:10px 0; color:var(--color-text-secondary); font:11px var(--font-mono); }.stage-actions { display:flex; flex-direction:column; align-items:flex-start; }.stage-actions .el-button { margin-left:0; }
.hash { font:11px var(--font-mono); color:var(--color-text-secondary); word-break:break-all; }.diff-panel,.history-diff { border:1px solid var(--color-border); border-radius:12px; padding:14px; }.panel-title { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }.panel-title span { color:var(--color-text-secondary); font-size:12px; }.test-editor { margin:16px 0; }.test-editor label { display:block; margin-bottom:8px; font-weight:600; }.test-output { max-height:240px; overflow:auto; background:var(--color-bg-light); color:var(--color-text-primary); border-radius:8px; padding:12px; }.history-diff { margin-top:18px; }
@media(max-width:900px){.workspace-header,.workspace-toolbar { align-items:flex-start; flex-direction:column; }.stage-card { grid-template-columns:32px minmax(0,1fr); }.stage-actions { grid-column:2; flex-direction:row; }.stage-row { grid-template-columns:1fr 1fr; }}
</style>
