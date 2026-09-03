<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createVendorConfig,
  deleteVendorConfig,
  getVendorConfigByInterface,
  testVendorConfig,
  updateVendorConfig,
  updateVendorConfigStatus
} from '@/api/vendor-config'
import { updateVendorRouting } from '@/api/interface'
import { useCacheStore } from '@/stores'
import { useUserStore } from '@/stores/user'
import type { ApiInterface, VendorConfigCreateRequest, VendorConfigSummary, VendorConfigUpdateRequest } from '@/types'
import VendorConnectorWorkspace from './config/VendorConnectorWorkspace.vue'
import {
  buildVendorRoutingPayload,
  dataTypeDisplayName,
  filterUnboundVendors,
  syncRoutingSelection,
  vendorDisplayName
} from '../interface-flow'

interface Props {
  modelValue: boolean
  interfaceData?: ApiInterface | null
  canEdit?: boolean
  canViewVendorConfig?: boolean
  canAddVendorConfig?: boolean
  canEditVendorConfig?: boolean
  canDeleteVendorConfig?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; success: [] }>()
const cacheStore = useCacheStore()
const userStore = useUserStore()
const { vendorOptions } = storeToRefs(cacheStore)
const loading = ref(false)
const submitting = ref(false)
const configList = ref<VendorConfigSummary[]>([])
const routing = ref<{ primaryVendorConfigId?: number; fallbackVendorConfigId?: number }>({})
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const editingConfig = ref<VendorConfigSummary | null>(null)
const connectorVisible = ref(false)
const connectorConfig = ref<VendorConfigSummary | null>(null)

const canManageConnector = computed(() => userStore.hasPermission('connector-plugin:view'))

const form = ref({
  vendorId: undefined as number | undefined,
  timeout: 30000,
  retryCount: 3,
  circuitThreshold: 5,
  circuitTimeout: 60
})

const rules: FormRules = {
  vendorId: [{ required: true, message: '请选择厂商', trigger: 'change' }],
  timeout: [{ required: true, message: '请输入超时时间', trigger: 'blur' }],
  retryCount: [{ required: true, message: '请输入重试次数', trigger: 'blur' }],
  circuitThreshold: [{ required: true, message: '请输入熔断阈值', trigger: 'blur' }],
  circuitTimeout: [{ required: true, message: '请输入熔断时间', trigger: 'blur' }]
}

const availableVendorOptions = computed(() => filterUnboundVendors(
  vendorOptions.value,
  configList.value,
  editingConfig.value?.vendorId
))

function syncRouting() {
  routing.value = syncRoutingSelection(configList.value, props.interfaceData ?? undefined)
}

async function loadConfigList() {
  if (!props.interfaceData?.id) return
  loading.value = true
  try {
    configList.value = (await getVendorConfigByInterface(props.interfaceData.id)).data || []
    syncRouting()
  } catch {
    console.error('加载厂商配置失败')
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, value => {
  if (value && props.interfaceData?.id) Promise.all([loadConfigList(), cacheStore.loadAll()])
})

function resetForm(config?: VendorConfigSummary) {
  editingConfig.value = config || null
  form.value = {
    vendorId: config?.vendorId,
    timeout: config?.timeout ?? 30000,
    retryCount: config?.retryCount ?? 3,
    circuitThreshold: config?.circuitThreshold ?? 5,
    circuitTimeout: config?.circuitTimeout ?? 60
  }
  formVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value || !props.interfaceData?.id) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const policy: VendorConfigUpdateRequest = {
      timeout: form.value.timeout,
      retryCount: form.value.retryCount,
      circuitThreshold: form.value.circuitThreshold,
      circuitTimeout: form.value.circuitTimeout
    }
    if (editingConfig.value) {
      await updateVendorConfig(editingConfig.value.id, policy)
    } else {
      if (!form.value.vendorId) throw new Error('VENDOR_REQUIRED')
      const request: VendorConfigCreateRequest = {
        vendorId: form.value.vendorId,
        interfaceId: props.interfaceData.id,
        ...policy
      }
      await createVendorConfig(request)
    }
    ElMessage.success(editingConfig.value ? '执行策略已更新' : '厂商配置已创建，请发布连接器后再启用')
    formVisible.value = false
    await loadConfigList()
    emit('success')
  } catch {
    console.error('保存厂商配置失败')
  } finally {
    submitting.value = false
  }
}

async function handleTest(config: VendorConfigSummary) {
  try {
    const result = (await testVendorConfig(config.id)).data
    if (result.success) ElMessage.success(`连接器 V${result.pipelineVersion || config.connectorVersion || '—'} 测试成功`)
    else ElMessage.error(result.errorMsg || '连接器测试失败')
  } catch {
    console.error('连接器测试失败')
  }
}

async function handleDelete(config: VendorConfigSummary) {
  try {
    await ElMessageBox.confirm(`确认删除厂商“${vendorDisplayName(config.vendorName)}”的配置吗？`, '删除确认', { type: 'warning' })
    await deleteVendorConfig(config.id)
    ElMessage.success('删除成功')
    await loadConfigList()
    emit('success')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除厂商配置失败')
    }
  }
}

async function handleStatusChange(config: VendorConfigSummary) {
  try {
    await updateVendorConfigStatus(config.id, config.status)
    ElMessage.success(config.status === 'active' ? '已启用' : '已停用')
  } catch {
    config.status = config.status === 'active' ? 'inactive' : 'active'
    console.error('更新厂商配置状态失败')
  }
}

async function saveRouting() {
  if (!props.interfaceData?.id) return
  let payload
  try {
    payload = buildVendorRoutingPayload(routing.value.primaryVendorConfigId, routing.value.fallbackVendorConfigId)
  } catch (error) {
    ElMessage.warning(error instanceof Error && error.message === 'FALLBACK_VENDOR_CONFIG_MUST_DIFFER'
      ? '备用厂商不能与主厂商相同'
      : '请先选择主厂商')
    return
  }
  submitting.value = true
  try {
    await updateVendorRouting(props.interfaceData.id, payload)
    ElMessage.success('主备路由已保存')
    await loadConfigList()
    emit('success')
  } catch {
    console.error('保存主备路由失败')
  } finally {
    submitting.value = false
  }
}

function openConnector(config: VendorConfigSummary) {
  connectorConfig.value = config
  connectorVisible.value = true
}

async function handleConnectorChanged() {
  await loadConfigList()
  if (connectorConfig.value) {
    connectorConfig.value = configList.value.find(item => item.id === connectorConfig.value?.id) || connectorConfig.value
  }
  emit('success')
}
</script>

<template>
  <el-drawer :model-value="modelValue" direction="rtl" size="720px" @close="emit('update:modelValue', false)">
    <template #header>
      <div class="drawer-header">
        <div>
          <h3>厂商连接器</h3>
          <p>{{ interfaceData?.interfaceName }} <code>{{ interfaceData?.interfaceCode }}</code></p>
        </div>
        <el-button v-if="canAddVendorConfig" type="primary" @click="resetForm()">绑定厂商</el-button>
      </div>
    </template>

    <el-alert
      title="固定调用入口：POST /openapi/v1/query；请求体使用 apiCode 区分业务接口。"
      type="info"
      :closable="false"
      show-icon
      class="plugin-alert"
    >
      <template #default>绑定厂商后配置主/备用路由，再发布连接器；接口启用前必须具备可用主厂商连接器。</template>
    </el-alert>

    <el-card shadow="never" class="routing-card">
      <template #header><strong>主备路由</strong></template>
      <el-form label-width="100px">
        <el-form-item label="主厂商" required>
          <el-select v-model="routing.primaryVendorConfigId" placeholder="请选择主厂商连接器" style="width: 100%" :disabled="!canEdit">
            <el-option
              v-for="config in configList"
              :key="config.id"
              :label="vendorDisplayName(config.vendorName)"
              :value="config.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备用厂商">
          <el-select v-model="routing.fallbackVendorConfigId" placeholder="可选" clearable style="width: 100%" :disabled="!canEdit">
            <el-option
              v-for="config in configList"
              :key="config.id"
              :label="vendorDisplayName(config.vendorName)"
              :value="config.id"
              :disabled="config.id === routing.primaryVendorConfigId"
            />
          </el-select>
        </el-form-item>
        <el-button v-if="canEdit" type="primary" :loading="submitting" @click="saveRouting">保存主备路由</el-button>
      </el-form>
    </el-card>

    <div v-loading="loading" class="config-list">
      <el-empty v-if="configList.length === 0" description="暂无厂商配置" />
      <el-card v-for="config in configList" :key="config.id" shadow="hover" class="config-card">
        <template #header>
          <div class="card-header">
            <div class="card-title">
              <strong>{{ vendorDisplayName(config.vendorName) }}</strong>
              <el-tag type="success" size="small">PLUGIN</el-tag>
              <el-tag size="small" effect="plain">{{ config.routingRole || 'UNASSIGNED' }}</el-tag>
              <el-tag :type="config.activeConnectorVersionId ? 'success' : 'warning'" size="small" effect="plain">
                {{ config.activeConnectorVersionId ? `连接器 V${config.connectorVersion}` : '未发布' }}
              </el-tag>
            </div>
            <el-switch
              v-model="config.status"
              active-value="active"
              inactive-value="inactive"
              :disabled="!canEditVendorConfig"
              @change="handleStatusChange(config)"
            />
          </div>
        </template>

        <el-descriptions :column="2" size="small">
          <el-descriptions-item label="数据类型">{{ dataTypeDisplayName(config.dataTypeName) }}</el-descriptions-item>
          <el-descriptions-item label="超时">{{ config.timeout }} ms</el-descriptions-item>
          <el-descriptions-item label="重试">{{ config.retryCount }} 次</el-descriptions-item>
          <el-descriptions-item label="熔断">{{ config.circuitThreshold }} 次 / {{ config.circuitTimeout }} 秒</el-descriptions-item>
          <el-descriptions-item label="状态">{{ config.status === 'active' ? '启用' : '停用' }}</el-descriptions-item>
        </el-descriptions>

        <div class="card-actions">
          <el-button v-if="canEditVendorConfig" @click="resetForm(config)">执行策略</el-button>
          <el-button v-if="canManageConnector" type="primary" @click="openConnector(config)">连接器配置</el-button>
          <el-button v-if="canEditVendorConfig" :disabled="!config.activeConnectorVersionId" @click="handleTest(config)">受控测试</el-button>
          <el-button v-if="canDeleteVendorConfig" type="danger" plain @click="handleDelete(config)">删除</el-button>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="formVisible" :title="editingConfig ? '编辑执行策略' : '绑定厂商'" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="厂商" prop="vendorId">
          <el-select v-model="form.vendorId" :disabled="Boolean(editingConfig) || !canAddVendorConfig" style="width: 100%">
            <el-option v-for="vendor in availableVendorOptions" :key="vendor.id" :label="vendorDisplayName(vendor.vendorName)" :value="Number(vendor.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据类型">
          <el-input :model-value="dataTypeDisplayName(interfaceData?.dataTypeName)" disabled />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="超时" prop="timeout"><el-input-number v-model="form.timeout" :min="100" :max="60000" :disabled="!canEditVendorConfig" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="重试" prop="retryCount"><el-input-number v-model="form.retryCount" :min="0" :max="10" :disabled="!canEditVendorConfig" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="熔断阈值" prop="circuitThreshold"><el-input-number v-model="form.circuitThreshold" :min="1" :max="100" :disabled="!canEditVendorConfig" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="熔断秒数" prop="circuitTimeout"><el-input-number v-model="form.circuitTimeout" :min="1" :max="3600" :disabled="!canEditVendorConfig" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button v-if="editingConfig ? canEditVendorConfig : canAddVendorConfig" type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <VendorConnectorWorkspace
      v-model="connectorVisible"
      :config="connectorConfig"
      @changed="handleConnectorChanged"
    />
  </el-drawer>
</template>

<style scoped>
.drawer-header,
.card-header,
.card-title,
.card-actions {
  display: flex;
  align-items: center;
}

.drawer-header,
.card-header {
  justify-content: space-between;
  width: 100%;
}

.drawer-header h3,
.drawer-header p {
  margin: 0;
}

.drawer-header p {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}

.plugin-alert {
  margin-bottom: 16px;
}

.config-list {
  min-height: 240px;
}

.config-card + .config-card {
  margin-top: 14px;
}

.card-title,
.card-actions {
  gap: 8px;
}

.card-actions {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
