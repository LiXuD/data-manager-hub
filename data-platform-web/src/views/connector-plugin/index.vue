<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  activateConnectorPluginVersion,
  disableConnectorPluginVersion,
  getConnectorPluginActivation,
  getConnectorPlugins,
  getConnectorPluginVersions,
  importConnectorPluginVersion,
  stageConnectorPluginVersion,
  verifyConnectorPluginVersion
} from '@/api/connector-plugin'
import type {
  ConnectorPlugin,
  ConnectorPluginActivationSummary,
  ConnectorPluginImportRequest,
  ConnectorPluginVersion
} from '@/types'
import {
  canActivate,
  canDisable,
  canStage,
  canVerify,
  disablePluginConfirmation,
  normalizedBindingCount,
  parseJsonDocument
} from '@/utils/connector'

const userStore = useUserStore()
const loading = ref(false)
const actionLoading = ref('')
const plugins = ref<ConnectorPlugin[]>([])
const selectedPluginId = ref('')
const versions = ref<ConnectorPluginVersion[]>([])
const activation = ref<ConnectorPluginActivationSummary | null>(null)
const activationVisible = ref(false)
const detailVersion = ref<ConnectorPluginVersion | null>(null)
const detailVisible = ref(false)
const importVisible = ref(false)
const importForm = reactive<ConnectorPluginImportRequest>({ artifactUri: '', expectedSha256: '', detachedSignature: '', signingKeyId: '' })

const allowed = (permission: string) => userStore.hasPermission(permission)
const selectedPlugin = computed(() => plugins.value.find(item => item.pluginId === selectedPluginId.value))

function statusType(status: string) {
  const types: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    ACTIVE: 'success', VERIFIED: 'primary', STAGING: 'warning', STAGING_FAILED: 'danger', DISABLED: 'info'
  }
  return types[status] || 'info'
}

async function loadPlugins(preferred?: string) {
  loading.value = true
  try {
    const response = await getConnectorPlugins()
    plugins.value = response.data || []
    selectedPluginId.value = preferred || selectedPluginId.value || plugins.value[0]?.pluginId || ''
    await loadVersions()
  } catch (error) {
    console.warn('加载连接器插件失败', error)
  } finally {
    loading.value = false
  }
}

async function loadVersions() {
  if (!selectedPluginId.value) {
    versions.value = []
    return
  }
  const response = await getConnectorPluginVersions(selectedPluginId.value)
  versions.value = response.data || []
}

async function runAction(version: ConnectorPluginVersion, action: 'verify' | 'stage' | 'activate' | 'disable') {
  const key = `${version.version}:${action}`
  actionLoading.value = key
  try {
    if (action === 'verify') await verifyConnectorPluginVersion(version.pluginId, version.version)
    if (action === 'stage') {
      activation.value = (await stageConnectorPluginVersion(version.pluginId, version.version)).data
      activationVisible.value = true
    }
    if (action === 'activate') {
      await ElMessageBox.confirm(`激活 ${version.pluginId}@${version.version} 作为新请求使用版本？`, '激活确认', { type: 'warning' })
      await activateConnectorPluginVersion(version.pluginId, version.version)
    }
    if (action === 'disable') {
      await ElMessageBox.confirm(disablePluginConfirmation(version.pluginId, version.version), '禁用确认', { type: 'warning' })
      await disableConnectorPluginVersion(version.pluginId, version.version)
    }
    ElMessage.success('操作成功')
    await loadPlugins(version.pluginId)
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  } finally {
    actionLoading.value = ''
  }
}

async function showActivation(version: ConnectorPluginVersion) {
  activation.value = (await getConnectorPluginActivation(version.pluginId, version.version)).data
  activationVisible.value = true
}

function versionReady(version: ConnectorPluginVersion) {
  return Boolean(activation.value && activation.value.pluginId === version.pluginId
    && activation.value.pluginVersion === version.version && activation.value.ready)
}

async function submitImport() {
  actionLoading.value = 'import'
  try {
    const response = await importConnectorPluginVersion(importForm)
    ElMessage.success('签名插件版本已导入')
    importVisible.value = false
    Object.assign(importForm, { artifactUri: '', expectedSha256: '', detachedSignature: '', signingKeyId: '' })
    await loadPlugins(response.data.pluginId)
  } finally {
    actionLoading.value = ''
  }
}

async function refreshActivation() {
  if (!activation.value) return
  activation.value = (await getConnectorPluginActivation(activation.value.pluginId, activation.value.pluginVersion)).data
}

onMounted(() => loadPlugins())
</script>

<template>
  <div class="page-container connector-page">
    <div class="page-header connector-hero">
      <div>
        <div class="eyebrow">CONTROL PLANE</div>
        <h1>连接器插件</h1>
        <p>管理受信制品、固定版本及各 Access 实例的加载事实。</p>
      </div>
      <el-button v-if="allowed('connector-plugin:import')" type="primary" @click="importVisible = true">导入签名版本</el-button>
    </div>

    <div class="connector-grid" v-loading="loading">
      <section class="plugin-catalog">
        <div class="section-title">插件目录 <span>{{ plugins.length }}</span></div>
        <button
          v-for="plugin in plugins"
          :key="plugin.pluginId"
          class="plugin-card"
          :class="{ active: plugin.pluginId === selectedPluginId }"
          type="button"
          @click="selectedPluginId = plugin.pluginId; loadVersions()"
        >
          <div class="plugin-card-top"><strong>{{ plugin.displayName }}</strong><el-tag :type="statusType(plugin.status)" size="small">{{ plugin.status }}</el-tag></div>
          <code>{{ plugin.pluginId }}</code>
          <p>{{ plugin.description || '暂无描述' }}</p>
          <div class="plugin-meta"><span>{{ plugin.provider }}</span><span>活动版本 {{ plugin.activeVersion || '—' }}</span></div>
          <div class="binding-count">活动厂商绑定 {{ normalizedBindingCount(plugin.bindingCount) }}</div>
        </button>
        <el-empty v-if="!plugins.length" description="尚未导入插件" :image-size="80" />
      </section>

      <section class="version-panel">
        <div class="section-title">
          <div>
            <span>{{ selectedPlugin?.displayName || '插件版本' }}</span>
            <code v-if="selectedPlugin">{{ selectedPlugin.pluginId }}</code>
            <el-tag v-if="selectedPlugin" size="small" effect="plain">活动厂商绑定 {{ normalizedBindingCount(selectedPlugin.bindingCount) }}</el-tag>
          </div>
          <el-button link @click="loadVersions">刷新</el-button>
        </div>
        <el-table :data="versions" empty-text="请选择插件或导入版本">
          <el-table-column label="版本" min-width="105"><template #default="{ row }"><strong>{{ row.version }}</strong><div class="muted">SPI {{ row.spiVersion }}</div></template></el-table-column>
          <el-table-column label="能力" min-width="230"><template #default="{ row }"><div class="capabilities"><el-tag v-for="item in row.capabilities" :key="item" size="small" effect="plain">{{ item }}</el-tag></div></template></el-table-column>
          <el-table-column prop="minHostVersion" label="最低宿主" width="100" />
          <el-table-column label="状态" width="125"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="制品" min-width="160"><template #default="{ row }"><div class="digest" :title="row.artifactSha256">{{ row.artifactSha256 }}</div><div class="muted">{{ row.signingKeyId }}</div></template></el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link @click="detailVersion = row; detailVisible = true">详情</el-button>
              <el-button link @click="showActivation(row)">实例</el-button>
              <el-button v-if="allowed('connector-plugin:verify') && canVerify(row.status)" link :loading="actionLoading === `${row.version}:verify`" @click="runAction(row, 'verify')">校验</el-button>
              <el-button v-if="allowed('connector-plugin:activate') && canStage(row.status)" link type="warning" :loading="actionLoading === `${row.version}:stage`" @click="runAction(row, 'stage')">预加载</el-button>
              <el-button v-if="allowed('connector-plugin:activate') && canActivate(row.status, versionReady(row))" link type="success" @click="runAction(row, 'activate')">激活</el-button>
              <el-button v-if="allowed('connector-plugin:disable') && canDisable(row.status)" link type="danger" @click="runAction(row, 'disable')">禁用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog v-model="importVisible" title="从受信制品库导入" width="620px">
      <el-alert type="info" :closable="false" show-icon>仅接受白名单内的 HTTPS Nexus/S3 兼容地址，不上传本地 JAR。</el-alert>
      <el-form label-position="top" class="import-form">
        <el-form-item label="制品地址" required><el-input v-model="importForm.artifactUri" placeholder="https://repo.example.com/connectors/demo-1.0.0.jar" /></el-form-item>
        <el-form-item label="预期 SHA-256" required><el-input v-model="importForm.expectedSha256" /></el-form-item>
        <el-form-item label="签名密钥 ID" required><el-input v-model="importForm.signingKeyId" /></el-form-item>
        <el-form-item label="脱离签名" required><el-input v-model="importForm.detachedSignature" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="importVisible = false">取消</el-button><el-button type="primary" :loading="actionLoading === 'import'" @click="submitImport">导入并校验</el-button></template>
    </el-dialog>

    <el-drawer v-model="activationVisible" title="逐实例激活状态" size="720px">
      <div v-if="activation" class="activation-summary"><el-tag :type="activation.ready ? 'success' : 'warning'">{{ activation.ready ? '全部就绪' : '尚未就绪' }}</el-tag><strong>{{ activation.pluginId }}@{{ activation.pluginVersion }}</strong><el-button link @click="refreshActivation">刷新</el-button></div>
      <el-table :data="activation?.instances || []">
        <el-table-column prop="serviceInstanceId" label="Access 实例" min-width="140" />
        <el-table-column prop="hostVersion" label="宿主版本" width="110" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.state === 'READY' ? 'success' : row.state === 'FAILED' ? 'danger' : 'warning'">{{ row.state }}</el-tag></template></el-table-column>
        <el-table-column prop="lastHeartbeatAt" label="最后心跳" min-width="170" />
        <el-table-column label="安全错误" min-width="170"><template #default="{ row }">{{ row.safeErrorCode || '—' }}<div class="muted">{{ row.safeErrorDigest }}</div></template></el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="detailVisible" title="插件版本详情" width="780px">
      <template v-if="detailVersion">
        <el-descriptions :column="2" border><el-descriptions-item label="入口类">{{ detailVersion.entryClass }}</el-descriptions-item><el-descriptions-item label="活动厂商绑定">{{ normalizedBindingCount(selectedPlugin?.bindingCount) }}</el-descriptions-item><el-descriptions-item label="制品地址" :span="2">{{ detailVersion.artifactUri }}</el-descriptions-item><el-descriptions-item label="验证时间">{{ detailVersion.verifiedAt || '—' }}</el-descriptions-item><el-descriptions-item label="错误摘要">{{ detailVersion.safeErrorDigest || '—' }}</el-descriptions-item></el-descriptions>
        <el-tabs class="detail-tabs"><el-tab-pane label="Manifest"><pre>{{ JSON.stringify(parseJsonDocument(detailVersion.manifestJson, {}), null, 2) }}</pre></el-tab-pane><el-tab-pane label="配置 Schema"><pre>{{ JSON.stringify(parseJsonDocument(detailVersion.configSchemaJson, {}), null, 2) }}</pre></el-tab-pane><el-tab-pane label="权限清单"><pre>{{ JSON.stringify(parseJsonDocument(detailVersion.permissionManifestJson, {}), null, 2) }}</pre></el-tab-pane></el-tabs>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.connector-page { padding: 24px; }
.connector-hero { display:flex; justify-content:space-between; align-items:flex-end; margin-bottom:20px; padding:24px 28px; border:1px solid var(--color-border); border-radius:16px; background:linear-gradient(125deg, rgba(0,212,170,.13), transparent 52%), var(--color-bg-card); }
.connector-hero h1 { margin:4px 0 6px; font-size:28px; color:var(--color-text-primary); }
.connector-hero p { margin:0; color:var(--color-text-secondary); }
.eyebrow { font:600 11px var(--font-mono); letter-spacing:.16em; color:#00b894; }
.connector-grid { display:grid; grid-template-columns:300px minmax(0,1fr); gap:18px; }
.plugin-catalog,.version-panel { background:var(--color-bg-card); border:1px solid var(--color-border); border-radius:14px; padding:16px; min-width:0; }
.section-title { min-height:32px; display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; font-weight:600; color:var(--color-text-primary); }
.section-title span { margin-right:8px; }.section-title code,.plugin-card code { font:12px var(--font-mono); color:#00a896; }
.plugin-card { width:100%; text-align:left; color:inherit; border:1px solid var(--color-border); background:var(--color-bg-light); border-radius:10px; padding:14px; margin-bottom:10px; cursor:pointer; }
.plugin-card:hover,.plugin-card.active { border-color:#00b894; box-shadow:0 0 0 1px rgba(0,184,148,.15); }
.plugin-card-top,.plugin-meta,.activation-summary { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.plugin-card p { color:var(--color-text-secondary); font-size:12px; line-height:1.5; min-height:36px; }
.plugin-meta,.muted { color:var(--color-text-secondary); font-size:11px; }
.binding-count { margin-top:8px; padding-top:8px; border-top:1px dashed var(--color-border); color:#00a896; font-size:11px; font-weight:600; }
.capabilities { display:flex; flex-wrap:wrap; gap:4px; }.digest { max-width:150px; overflow:hidden; text-overflow:ellipsis; font:11px var(--font-mono); }
.import-form { margin-top:18px; }.activation-summary { justify-content:flex-start; margin-bottom:16px; }.activation-summary .el-button { margin-left:auto; }
.detail-tabs pre { max-height:360px; overflow:auto; padding:14px; border-radius:8px; color:var(--color-text-primary); background:var(--color-bg-light); font:12px/1.6 var(--font-mono); }
@media (max-width: 1000px) { .connector-grid { grid-template-columns:1fr; }.plugin-catalog { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }.plugin-catalog .section-title,.plugin-catalog .el-empty { grid-column:1/-1; }.plugin-card { margin:0; } }
</style>
