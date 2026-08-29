<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getInterfaceList } from '@/api/interface'
import { getVendorConfigByInterface } from '@/api/vendor-config'
import {
  getConnectorExecutionPlan,
  getConnectorSpecCatalog,
  getConnectorSpecDraft,
  getConnectorSpecHistory
} from '@/api/connector-spec'
import { useUserStore } from '@/stores/user'
import type {
  ApiInterface,
  ConnectorExecutionPlan,
  ConnectorSpecCatalog,
  ConnectorSpecCatalogEntry,
  ConnectorSpecDraftView,
  ConnectorSpecHistoryVersion,
  VendorConfigSummary
} from '@/types'
import { extractPageData } from '@/utils/pagination'

const userStore = useUserStore()
const interfaces = ref<ApiInterface[]>([])
const configs = ref<VendorConfigSummary[]>([])
const selectedInterfaceId = ref<number>()
const selectedConfigId = ref<number>()
const catalog = ref<ConnectorSpecCatalogEntry[]>([])
const draft = ref<ConnectorSpecDraftView | null>(null)
const history = ref<ConnectorSpecHistoryVersion[]>([])
const plan = ref<ConnectorExecutionPlan | null>(null)
const planVersion = ref<number>()
const loading = ref(false)
const diagnosticLoading = ref(false)
const planLoading = ref(false)
const loadError = ref('')
const planError = ref('')
let diagnosticRequest = 0

const canViewDiagnostic = computed(() => userStore.hasPermission('system:admin')
  && userStore.hasPermission('connector-plugin:view'))
const selectedInterface = computed(() => interfaces.value.find(item => item.id === selectedInterfaceId.value))
const selectedConfig = computed(() => configs.value.find(item => item.id === selectedConfigId.value))
const selectedPlugin = computed(() => draft.value?.connectorSpec?.plugin)
const selectedCatalog = computed(() => catalog.value.find(item => item.pluginId === selectedPlugin.value?.pluginId))
const activeVersion = computed(() => history.value.find(item => item.status === 'ACTIVE'))
const firstPlanStage = computed(() => plan.value?.stages?.[0])
const runtimePlugin = computed(() => selectedPlugin.value
  ? `${selectedPlugin.value.pluginId}@${selectedPlugin.value.pluginVersion}`
  : firstPlanStage.value
    ? `${firstPlanStage.value.pluginId}@${firstPlanStage.value.pluginVersion}`
    : '—')
const planLabel = computed(() => {
  if (plan.value?.version != null) return `V${plan.value.version}`
  if (plan.value?.draftVersion != null) return `草稿 V${plan.value.draftVersion}`
  return '当前活动计划'
})

function shortHash(value?: string) {
  return value ? value.slice(0, 12) : '—'
}

function joinValues(values?: string[]) {
  return values?.length ? values.join('、') : '—'
}

function configLabel(config: VendorConfigSummary) {
  return `${config.vendorName || `厂商 #${config.vendorId}`} · ${config.routingRole || '未分配'}`
}

async function loadInterfaces() {
  if (!canViewDiagnostic.value) return
  loading.value = true
  loadError.value = ''
  try {
    const response = await getInterfaceList({ page: 1, pageSize: 100, status: 'active' })
    interfaces.value = extractPageData<ApiInterface>(response).list
    if (!interfaces.value.some(item => item.id === selectedInterfaceId.value)) {
      selectedInterfaceId.value = interfaces.value[0]?.id
    }
  } catch (error) {
    loadError.value = '业务接口列表加载失败，请确认接口查询权限和服务状态。'
    console.warn('加载连接器诊断接口列表失败', error)
  } finally {
    loading.value = false
  }
}

async function loadConfigs(interfaceId: number) {
  const requestId = ++diagnosticRequest
  loading.value = true
  loadError.value = ''
  configs.value = []
  selectedConfigId.value = undefined
  resetDiagnostics()
  try {
    configs.value = (await getVendorConfigByInterface(interfaceId)).data || []
    if (requestId !== diagnosticRequest) return
    selectedConfigId.value = configs.value[0]?.id
  } catch (error) {
    if (requestId === diagnosticRequest) loadError.value = '厂商连接器列表加载失败，请确认连接器查看权限。'
    console.warn('加载连接器诊断配置列表失败', error)
  } finally {
    if (requestId === diagnosticRequest) loading.value = false
  }
}

function resetDiagnostics() {
  catalog.value = []
  draft.value = null
  history.value = []
  plan.value = null
  planVersion.value = undefined
  planError.value = ''
}

async function loadDiagnostics(configId: number) {
  const requestId = ++diagnosticRequest
  diagnosticLoading.value = true
  loadError.value = ''
  resetDiagnostics()
  const results = await Promise.allSettled([
    getConnectorSpecCatalog(configId),
    getConnectorSpecDraft(configId),
    getConnectorSpecHistory(configId),
    getConnectorExecutionPlan(configId)
  ])
  if (requestId !== diagnosticRequest) return

  const [catalogResult, draftResult, historyResult, planResult] = results
  if (catalogResult.status === 'fulfilled') {
    const response: { data: ConnectorSpecCatalog } = catalogResult.value
    catalog.value = response.data?.plugins || []
  }
  if (draftResult.status === 'fulfilled') draft.value = draftResult.value.data
  if (historyResult.status === 'fulfilled') history.value = historyResult.value.data?.versions || []
  if (planResult.status === 'fulfilled') plan.value = planResult.value.data
  else planError.value = '当前配置暂无可读取的执行计划，可能尚未保存或发布产品版本。'

  if (results.every(result => result.status === 'rejected')) {
    loadError.value = '连接器诊断数据加载失败，请确认管理员权限和 Masterdata 服务状态。'
  }
  diagnosticLoading.value = false
}

async function loadPlan(version?: number) {
  if (!selectedConfigId.value) return
  planLoading.value = true
  planError.value = ''
  try {
    plan.value = (await getConnectorExecutionPlan(selectedConfigId.value, version)).data
    planVersion.value = version
  } catch (error) {
    plan.value = null
    planVersion.value = version
    planError.value = '执行计划加载失败，请稍后重试。'
    console.warn('加载连接器执行计划失败', error)
  } finally {
    planLoading.value = false
  }
}

watch(selectedInterfaceId, value => {
  if (value != null) void loadConfigs(value)
  else {
    configs.value = []
    selectedConfigId.value = undefined
    resetDiagnostics()
  }
})

watch(selectedConfigId, value => {
  if (value != null) void loadDiagnostics(value)
  else resetDiagnostics()
})

onMounted(() => void loadInterfaces())
</script>

<template>
  <div class="page-container diagnostics-page">
    <div class="page-header diagnostics-hero">
      <div>
        <div class="eyebrow">ADMIN DIAGNOSTICS</div>
        <h1>连接器运行诊断</h1>
        <p>查看平台编译出的固定执行计划、传输策略和版本摘要；普通配置页不暴露这些内部事实。</p>
      </div>
      <el-tag type="warning" effect="dark">仅管理员</el-tag>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="只读排障页面"
      description="此页面只展示阶段坐标、能力、顺序、传输和摘要信息，不展示连接器配置值、密钥或可编辑控件。"
    />

    <el-result v-if="!canViewDiagnostic" icon="warning" title="无权访问" sub-title="需要 system:admin 和 connector-plugin:view 权限" />

    <template v-else>
      <el-card shadow="never" class="selector-card" v-loading="loading">
        <el-form inline label-position="top">
          <el-form-item label="业务接口">
            <el-select v-model="selectedInterfaceId" filterable clearable placeholder="请选择业务接口" class="interface-select">
              <el-option
                v-for="item in interfaces"
                :key="item.id"
                :label="`${item.interfaceName} · ${item.interfaceCode}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="厂商连接器">
            <el-select v-model="selectedConfigId" filterable clearable placeholder="请选择厂商连接器" class="config-select">
              <el-option v-for="item in configs" :key="item.id" :label="configLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-button @click="loadInterfaces">刷新</el-button>
        </el-form>
      </el-card>

      <el-alert v-if="loadError" type="error" :closable="false" show-icon>{{ loadError }}</el-alert>
      <el-empty v-if="!selectedConfigId && !loading" description="请选择一个业务接口和厂商连接器" />

      <div v-if="selectedConfig" v-loading="diagnosticLoading" class="diagnostic-content">
        <section class="summary-card">
          <div class="section-heading">
            <div>
              <div class="section-kicker">RUNTIME FACTS</div>
              <h2>{{ selectedConfig.vendorName || `厂商 #${selectedConfig.vendorId}` }} · {{ selectedConfig.dataTypeName || selectedInterface?.dataTypeName || '数据类型未加载' }}</h2>
            </div>
            <el-tag :type="selectedConfig.status === 'active' ? 'success' : 'info'">{{ selectedConfig.status }}</el-tag>
          </div>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="业务接口">{{ selectedInterface?.interfaceName || selectedConfig.interfaceName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="接口编码">{{ selectedInterface?.interfaceCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="路由角色">{{ selectedConfig.routingRole || 'UNASSIGNED' }}</el-descriptions-item>
            <el-descriptions-item label="运行模式">{{ selectedConfig.runtimeMode }}</el-descriptions-item>
            <el-descriptions-item label="活动连接器版本">{{ activeVersion ? `V${activeVersion.version}` : '—' }}</el-descriptions-item>
            <el-descriptions-item label="草稿创作模式">{{ draft?.authoringMode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="固定插件版本" :span="3">{{ runtimePlugin }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="diagnostic-panel">
          <div class="section-heading">
            <div>
              <div class="section-kicker">COMPILED CATALOG</div>
              <h2>选定插件运行事实</h2>
            </div>
            <el-tag v-if="selectedCatalog" effect="plain">{{ selectedCatalog.pluginId }}</el-tag>
          </div>
          <el-descriptions v-if="selectedCatalog" :column="3" border>
            <el-descriptions-item label="连接器类型">{{ selectedCatalog.connectorKind }}</el-descriptions-item>
            <el-descriptions-item label="TRANSPORT">{{ selectedCatalog.transportMode }}</el-descriptions-item>
            <el-descriptions-item label="输出模式">{{ selectedCatalog.outputMode }}</el-descriptions-item>
            <el-descriptions-item label="兼容厂商">{{ joinValues(selectedCatalog.compatibility.vendorCodes) }}</el-descriptions-item>
            <el-descriptions-item label="兼容数据类型">{{ joinValues(selectedCatalog.compatibility.dataTypeCodes) }}</el-descriptions-item>
            <el-descriptions-item label="推荐版本">{{ selectedCatalog.recommendedVersion || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="当前草稿没有可匹配的插件目录事实" :image-size="72" />
        </section>

        <section class="diagnostic-panel">
          <div class="section-heading">
            <div>
              <div class="section-kicker">EXECUTION PLAN</div>
              <h2>平台编译执行计划</h2>
            </div>
            <div class="heading-actions">
              <el-tag v-if="plan" type="success">{{ planLabel }}</el-tag>
              <el-button :loading="planLoading" @click="loadPlan()">刷新当前计划</el-button>
            </div>
          </div>
          <el-alert v-if="planError" type="info" :closable="false">{{ planError }}</el-alert>
          <el-table v-else v-loading="planLoading" :data="plan?.stages || []" empty-text="暂无执行计划">
            <el-table-column prop="order" label="顺序" width="75" />
            <el-table-column prop="stageKey" label="阶段标识" min-width="190" />
            <el-table-column prop="capability" label="能力" min-width="160" />
            <el-table-column label="固定插件" min-width="200"><template #default="{ row }">{{ row.pluginId }}@{{ row.pluginVersion }}</template></el-table-column>
            <el-table-column prop="source" label="来源" min-width="180" />
            <el-table-column label="配置摘要" min-width="145"><template #default="{ row }"><span class="digest">{{ shortHash(row.configHash) }}</span></template></el-table-column>
            <el-table-column label="制品 / Schema 摘要" min-width="220"><template #default="{ row }"><span class="digest">{{ shortHash(row.artifactHashPrefix) }} / {{ shortHash(row.schemaHashPrefix) }}</span></template></el-table-column>
          </el-table>
        </section>

        <section class="diagnostic-panel">
          <div class="section-heading">
            <div>
              <div class="section-kicker">IMMUTABLE HISTORY</div>
              <h2>不可变版本摘要</h2>
            </div>
            <span class="muted">{{ history.length }} 个版本</span>
          </div>
          <el-table :data="history" empty-text="暂无产品版本历史">
            <el-table-column prop="version" label="版本" width="85"><template #default="{ row }">V{{ row.version }}</template></el-table-column>
            <el-table-column prop="authoringMode" label="创作模式" min-width="160" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="插件版本" min-width="210"><template #default="{ row }">{{ row.connectorSpec?.plugin ? `${row.connectorSpec.plugin.pluginId}@${row.connectorSpec.plugin.pluginVersion}` : 'Legacy / 未固化' }}</template></el-table-column>
            <el-table-column label="快照摘要" min-width="180"><template #default="{ row }"><span class="digest">{{ shortHash(row.snapshotHash) }}</span></template></el-table-column>
            <el-table-column prop="publishedAt" label="发布时间" min-width="180" />
            <el-table-column label="操作" width="110"><template #default="{ row }"><el-button link :loading="planLoading && planVersion === row.version" @click="loadPlan(row.version)">查看计划</el-button></template></el-table-column>
          </el-table>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.diagnostics-page { padding:24px; display:grid; gap:18px; }
.diagnostics-hero { display:flex; justify-content:space-between; align-items:flex-end; padding:24px 28px; border:1px solid var(--color-border); border-radius:16px; background:linear-gradient(125deg,rgba(255,184,77,.14),transparent 55%),var(--color-bg-card); }
.diagnostics-hero h1 { margin:4px 0 6px; }.diagnostics-hero p { margin:0; color:var(--color-text-secondary); }.eyebrow,.section-kicker { color:#f0a24b; font:600 11px var(--font-mono); letter-spacing:.16em; }
.selector-card,.summary-card,.diagnostic-panel { border:1px solid var(--color-border); border-radius:12px; background:var(--color-bg-card); }.selector-card { padding:18px 20px; }.selector-card .el-form { display:flex; align-items:flex-end; gap:14px; }.selector-card .el-form-item { margin:0; }.interface-select { width:330px; }.config-select { width:300px; }
.diagnostic-content { display:grid; gap:18px; }.summary-card,.diagnostic-panel { padding:20px; }.section-heading { display:flex; justify-content:space-between; align-items:flex-end; gap:14px; margin-bottom:16px; }.section-heading h2 { margin:4px 0 0; font-size:18px; }.heading-actions { display:flex; align-items:center; gap:10px; }.muted { color:var(--color-text-secondary); font-size:12px; }.digest { font:11px var(--font-mono); color:var(--color-text-secondary); word-break:break-all; }
@media(max-width:900px){.diagnostics-hero,.section-heading,.selector-card .el-form{align-items:flex-start;flex-direction:column}.selector-card .el-form-item,.interface-select,.config-select{width:100%}.heading-actions{flex-wrap:wrap}}
</style>
