<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
  getVendorConfigByInterface,
  createVendorConfig,
  updateVendorConfig,
  deleteVendorConfig,
  updateVendorConfigStatus,
  testVendorConfig
} from '@/api/vendor-config'
import { useCacheStore } from '@/stores'
import { getStatusType } from '@/utils/status'
import type {
  ApiInterface,
  VendorInterfaceConfig,
  HttpMethod,
  HeaderConfigItem,
  RequestMappingItem,
  ResponseMappingItem,
  AuthConfig,
  SignConfig,
  ContentType
} from '@/types'
import HeaderEditor from './config/HeaderEditor.vue'
import ParamsMappingEditor from './config/ParamsMappingEditor.vue'
import AuthConfigComponent from './config/AuthConfig.vue'
import SignConfigComponent from './config/SignConfig.vue'
import RequestBodyEditor from './config/RequestBodyEditor.vue'

interface Props {
  modelValue: boolean
  interfaceData?: ApiInterface | null
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const cacheStore = useCacheStore()
const { vendorOptions, dataTypeOptions } = storeToRefs(cacheStore)

const loading = ref(false)
const configList = ref<VendorInterfaceConfig[]>([])
const activeTab = ref('basic')

// 表单
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const isEdit = ref(false)
const currentConfig = ref<VendorInterfaceConfig | null>(null)

// 表单数据
const form = ref({
  // 基本信息
  vendorId: undefined as number | undefined,
  dataTypeId: undefined as number | undefined,
  apiUrl: '',
  method: 'POST' as HttpMethod,
  timeout: 30000,
  retryCount: 3,
  // 熔断配置
  circuitThreshold: 5,
  circuitTimeout: 60,
  // 请求配置
  headerList: [] as HeaderConfigItem[],
  requestBody: '',
  contentType: 'application/json' as ContentType,
  // 参数映射
  requestMapping: [] as RequestMappingItem[],
  responseMapping: [] as ResponseMappingItem[],
  // 签名配置
  signConfig: { type: 'NONE' as const } as SignConfig,
  // 认证配置
  authConfig: { type: 'NONE' as const } as AuthConfig,
  // 降级配置
  fallbackVendorId: undefined as number | undefined,
  // 状态
  status: 'active' as 'active' | 'inactive'
})

const rules: FormRules = {
  vendorId: [{ required: true, message: '请选择厂商', trigger: 'change' }],
  dataTypeId: [{ required: true, message: '请选择数据类型', trigger: 'change' }],
  apiUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }],
  method: [{ required: true, message: '请选择请求方法', trigger: 'change' }],
  timeout: [{ required: true, message: '请输入超时时间', trigger: 'blur' }],
  retryCount: [{ required: true, message: '请输入重试次数', trigger: 'blur' }],
  circuitThreshold: [{ required: true, message: '请输入熔断阈值', trigger: 'blur' }],
  circuitTimeout: [{ required: true, message: '请输入熔断时间', trigger: 'blur' }]
}

// 数据类型选项（不再按厂商过滤，因为 DataType 与 Vendor 无关联）
const filteredDataTypeOptions = computed(() => dataTypeOptions.value)

// 降级厂商选项
const fallbackVendorOptions = computed(() => {
  if (!form.value.vendorId) return vendorOptions.value
  return vendorOptions.value.filter(v => Number(v.id) !== form.value.vendorId)
})

// 加载配置列表
const loadConfigList = async () => {
  if (!props.interfaceData?.id) return

  loading.value = true
  try {
    const res = await getVendorConfigByInterface(props.interfaceData.id)
    configList.value = res.data || []
  } catch (error) {
    console.error('加载配置失败:', error)
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

// 监听弹窗打开
watch(() => props.modelValue, (val) => {
  if (val && props.interfaceData?.id) {
    Promise.all([loadConfigList(), cacheStore.loadAll()])
  }
})

// 关闭弹窗
const handleClose = () => {
  emit('update:modelValue', false)
}

// 新增配置
const handleAdd = () => {
  isEdit.value = false
  currentConfig.value = null
  form.value = {
    vendorId: undefined,
    dataTypeId: props.interfaceData?.dataTypeId,
    apiUrl: '',
    method: 'POST',
    timeout: 30000,
    retryCount: 3,
    circuitThreshold: 5,
    circuitTimeout: 60,
    headerList: [],
    requestBody: '',
    contentType: 'application/json',
    requestMapping: [],
    responseMapping: [],
    signConfig: { type: 'NONE' },
    authConfig: { type: 'NONE' },
    fallbackVendorId: undefined,
    status: 'active'
  }
  activeTab.value = 'basic'
  formVisible.value = true
}

// 编辑配置
const handleEdit = (config: VendorInterfaceConfig) => {
  isEdit.value = true
  currentConfig.value = config

  // 解析 JSON 配置
  let headerList: HeaderConfigItem[] = []
  let requestMapping: RequestMappingItem[] = []
  let responseMapping: ResponseMappingItem[] = []
  let authConfig: AuthConfig = { type: 'NONE' }
  let signConfig: SignConfig = { type: 'NONE' }
  let requestBody = ''
  let contentType: ContentType = 'application/json'

  try {
    if (config.headerConfig) {
      const parsed = JSON.parse(config.headerConfig)
      if (Array.isArray(parsed)) {
        headerList = parsed
      } else {
        headerList = Object.entries(parsed).map(([key, value]) => ({
          key,
          value: String(value),
          enabled: true
        }))
      }
    }
  } catch {}

  try {
    if (config.requestTemplate) {
      const parsed = JSON.parse(config.requestTemplate)
      if (parsed.body) {
        requestBody = typeof parsed.body === 'string' ? parsed.body : JSON.stringify(parsed.body, null, 2)
        contentType = parsed.contentType || 'application/json'
      }
      if (Array.isArray(parsed.requestMapping)) {
        requestMapping = parsed.requestMapping
      } else if (parsed.request) {
        requestMapping = Object.entries(parsed.request).map(([source, target]) => ({
          sourceVar: source,
          targetField: String(target),
          required: true,
          transformType: 'none' as const
        }))
      }
    }
  } catch {}

  try {
    if (config.responseMapping) {
      const parsed = JSON.parse(config.responseMapping)
      if (Array.isArray(parsed)) {
        responseMapping = parsed
      } else {
        responseMapping = Object.entries(parsed).map(([source, target]) => ({
          sourcePath: source,
          targetField: String(target),
          sourceType: 'field' as const,
          transformType: 'none' as const
        }))
      }
    }
  } catch {}

  try {
    if (config.authConfig) {
      authConfig = JSON.parse(config.authConfig)
    }
  } catch {}

  form.value = {
    vendorId: config.vendorId,
    dataTypeId: config.dataTypeId,
    apiUrl: config.apiUrl,
    method: config.method,
    timeout: config.timeout,
    retryCount: config.retryCount,
    circuitThreshold: config.circuitThreshold,
    circuitTimeout: config.circuitTimeout,
    headerList,
    requestBody,
    contentType,
    requestMapping,
    responseMapping,
    signConfig,
    authConfig,
    fallbackVendorId: config.fallbackVendorId,
    status: config.status
  }
  activeTab.value = 'basic'
  formVisible.value = true
}

// 测试连接
const handleTest = async (config: VendorInterfaceConfig) => {
  try {
    ElMessage.info('正在测试连接...')
    const res = await testVendorConfig(config.id)
    if (res.data?.success) {
      ElMessage.success(`连接成功，延迟: ${res.data.latency}ms`)
    } else {
      ElMessage.error(`连接失败: ${res.data?.error || '未知错误'}`)
    }
  } catch (error) {
    console.error('测试失败:', error)
    ElMessage.error('测试连接失败')
  }
}

// 删除配置
const handleDelete = async (config: VendorInterfaceConfig) => {
  try {
    await ElMessageBox.confirm(
      `确认删除厂商"${config.vendorName || `#${config.vendorId}`}"的配置吗？`,
      '删除确认',
      { type: 'warning' }
    )
    await deleteVendorConfig(config.id)
    ElMessage.success('删除成功')
    loadConfigList()
    emit('success')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 状态切换
const handleStatusChange = async (config: VendorInterfaceConfig) => {
  try {
    await updateVendorConfigStatus(config.id, config.status)
    ElMessage.success(config.status === 'active' ? '已启用' : '已禁用')
  } catch (error) {
    config.status = config.status === 'active' ? 'inactive' : 'active'
    console.error('状态更新失败:', error)
    ElMessage.error('状态更新失败')
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    // 构建请求数据
    const headerConfig = form.value.headerList
      .filter(h => h.enabled && h.key)
      .reduce((acc, h) => {
        acc[h.key] = h.value
        return acc
      }, {} as Record<string, string>)

    const validRequestMapping = form.value.requestMapping
      .filter(m => m.sourceVar && m.targetField)

    const validResponseMapping = form.value.responseMapping
      .filter(m => m.sourcePath && m.targetField)

    const requestTemplate: Record<string, unknown> = {}
    if (validRequestMapping.length > 0) {
      requestTemplate.requestMapping = validRequestMapping
    }
    if (form.value.requestBody) {
      requestTemplate.body = form.value.requestBody
      requestTemplate.contentType = form.value.contentType
    }

    const hasHeaderConfig = Object.keys(headerConfig).length > 0
    const hasRequestTemplate = Object.keys(requestTemplate).length > 0
    const hasResponseMapping = validResponseMapping.length > 0

    const data = {
      vendorId: form.value.vendorId,
      dataTypeId: form.value.dataTypeId,
      interfaceId: props.interfaceData!.id,
      apiUrl: form.value.apiUrl,
      method: form.value.method,
      timeout: form.value.timeout,
      retryCount: form.value.retryCount,
      circuitThreshold: form.value.circuitThreshold,
      circuitTimeout: form.value.circuitTimeout,
      signType: form.value.signConfig.type === 'NONE' ? undefined : form.value.signConfig.type,
      headerConfig: hasHeaderConfig ? JSON.stringify(headerConfig) : undefined,
      requestTemplate: hasRequestTemplate ? JSON.stringify(requestTemplate) : undefined,
      responseMapping: hasResponseMapping ? JSON.stringify(validResponseMapping) : undefined,
      authType: form.value.authConfig.type === 'NONE' ? undefined : form.value.authConfig.type,
      authConfig: form.value.authConfig.type !== 'NONE' ? JSON.stringify(form.value.authConfig) : undefined,
      fallbackVendorId: form.value.fallbackVendorId || undefined,
      status: form.value.status
    }

    if (isEdit.value && currentConfig.value) {
      await updateVendorConfig(currentConfig.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await createVendorConfig(data)
      ElMessage.success('创建成功')
    }

    formVisible.value = false
    loadConfigList()
    emit('success')
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title=""
    direction="rtl"
    size="720px"
    class="config-drawer"
    @close="handleClose"
  >
    <template #header>
      <div class="drawer-header">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 15a3 3 0 100-6 3 3 0 000 6z"/>
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/>
          </svg>
        </div>
        <div class="header-content">
          <h3>接口配置</h3>
          <p class="interface-info">{{ interfaceData?.interfaceName }} <span class="code">{{ interfaceData?.interfaceCode }}</span></p>
        </div>
      </div>
    </template>

    <div class="drawer-body" v-loading="loading">
      <!-- 已配置的厂商列表 -->
      <div class="config-section">
        <div class="section-header">
          <h4>已配置厂商</h4>
          <el-button type="primary" size="small" @click="handleAdd">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            添加配置
          </el-button>
        </div>

        <div v-if="configList.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <path d="M3 9h18M9 21V9"/>
          </svg>
          <p>暂无厂商配置</p>
          <span>点击上方按钮添加厂商接口配置</span>
        </div>

        <div v-else class="config-cards">
          <div
            v-for="config in configList"
            :key="config.id"
            class="config-card"
            :class="{ active: config.status === 'active' }"
          >
            <div class="card-header">
              <div class="vendor-info">
                <span class="vendor-name">{{ config.vendorName || `厂商 #${config.vendorId}` }}</span>
                <el-tag :type="config.status === 'active' ? 'success' : 'info'" size="small">
                  {{ config.status === 'active' ? '启用' : '禁用' }}
                </el-tag>
              </div>
              <div class="card-actions">
                <el-switch
                  v-model="config.status"
                  active-value="active"
                  inactive-value="inactive"
                  @change="handleStatusChange(config)"
                />
                <el-dropdown trigger="click">
                  <el-button type="primary" link size="small">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="action-icon">
                      <circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/>
                    </svg>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="handleEdit(config)">编辑配置</el-dropdown-item>
                      <el-dropdown-item @click="handleTest(config)">测试连接</el-dropdown-item>
                      <el-dropdown-item divided @click="handleDelete(config)">删除配置</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            <div class="card-body">
              <div class="config-item">
                <span class="label">API地址</span>
                <span class="value url">{{ config.apiUrl }}</span>
              </div>
              <div class="config-row">
                <div class="config-item">
                  <span class="label">请求方法</span>
                  <el-tag :type="getStatusType('httpMethod', config.method)" size="small">{{ config.method }}</el-tag>
                </div>
                <div class="config-item">
                  <span class="label">超时时间</span>
                  <span class="value">{{ config.timeout }}ms</span>
                </div>
                <div class="config-item">
                  <span class="label">重试次数</span>
                  <span class="value">{{ config.retryCount }}</span>
                </div>
              </div>
              <div v-if="config.fallbackVendorId" class="fallback-info">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M16 3h5v5M8 3H3v5M3 16v5h5M21 16v5h-5"/>
                </svg>
                <span>降级厂商: {{ config.fallbackVendorName || `#${config.fallbackVendorId}` }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑配置表单 -->
    <el-drawer
      v-model="formVisible"
      :title="isEdit ? '编辑配置' : '新增配置'"
      direction="rtl"
      size="640px"
      append-to-body
      class="form-drawer"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="config-form"
      >
        <el-tabs v-model="activeTab" class="config-tabs">
          <!-- 基本信息 Tab -->
          <el-tab-pane label="基本信息" name="basic">
            <el-form-item label="选择厂商" prop="vendorId">
              <el-select v-model="form.vendorId" placeholder="请选择厂商" style="width: 100%" :disabled="isEdit">
                <el-option
                  v-for="vendor in vendorOptions"
                  :key="vendor.id"
                  :label="vendor.vendorName"
                  :value="Number(vendor.id)"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="数据类型" prop="dataTypeId">
              <el-select v-model="form.dataTypeId" placeholder="请选择数据类型" style="width: 100%" :disabled="isEdit">
                <el-option
                  v-for="dt in filteredDataTypeOptions"
                  :key="dt.id"
                  :label="dt.dataTypeName"
                  :value="dt.id"
                />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">API配置</el-divider>

            <el-form-item label="API地址" prop="apiUrl">
              <el-input v-model="form.apiUrl" placeholder="请输入API地址，如 https://api.example.com/v1/query" />
            </el-form-item>

            <el-form-item label="请求方法" prop="method">
              <el-select v-model="form.method" style="width: 100%">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="超时时间" prop="timeout">
                  <el-input-number v-model="form.timeout" :min="100" :max="60000" :step="100" style="width: 100%">
                    <template #append>ms</template>
                  </el-input-number>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="重试次数" prop="retryCount">
                  <el-input-number v-model="form.retryCount" :min="0" :max="10" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">熔断配置</el-divider>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="熔断阈值" prop="circuitThreshold">
                  <el-input-number v-model="form.circuitThreshold" :min="1" :max="100" style="width: 100%" />
                  <div class="form-tip">连续失败次数达到阈值触发熔断</div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="熔断时间" prop="circuitTimeout">
                  <el-input-number v-model="form.circuitTimeout" :min="10" :max="3600" :step="10" style="width: 100%">
                    <template #append>秒</template>
                  </el-input-number>
                  <div class="form-tip">熔断后等待恢复的时间</div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <!-- 请求配置 Tab -->
          <el-tab-pane label="请求配置" name="request">
            <HeaderEditor v-model="form.headerList" />
            <div class="section-divider"></div>
            <RequestBodyEditor
              v-model="form.requestBody"
              v-model:content-type="form.contentType"
            />
          </el-tab-pane>

          <!-- 参数映射 Tab -->
          <el-tab-pane label="参数映射" name="mapping">
            <ParamsMappingEditor
              v-model:request-mapping="form.requestMapping"
              v-model:response-mapping="form.responseMapping"
            />
          </el-tab-pane>

          <!-- 签名配置 Tab -->
          <el-tab-pane label="签名配置" name="sign">
            <SignConfigComponent v-model="form.signConfig" />
          </el-tab-pane>

          <!-- 认证配置 Tab -->
          <el-tab-pane label="认证配置" name="auth">
            <AuthConfigComponent v-model="form.authConfig" />
          </el-tab-pane>

          <!-- 降级配置 Tab -->
          <el-tab-pane label="降级配置" name="fallback">
            <div class="fallback-section">
              <el-form-item label="降级厂商">
                <el-select v-model="form.fallbackVendorId" placeholder="请选择降级厂商" clearable style="width: 100%">
                  <el-option
                    v-for="vendor in fallbackVendorOptions"
                    :key="vendor.id"
                    :label="vendor.vendorName"
                    :value="Number(vendor.id)"
                  />
                </el-select>
                <div class="form-tip">当此厂商不可用时，自动切换到降级厂商</div>
              </el-form-item>

              <el-form-item label="状态">
                <el-radio-group v-model="form.status">
                  <el-radio value="active">启用</el-radio>
                  <el-radio value="inactive">禁用</el-radio>
                </el-radio-group>
              </el-form-item>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存配置</el-button>
      </template>
    </el-drawer>
  </el-drawer>
</template>

<style scoped>
.config-drawer :deep(.el-drawer__header) {
  padding: 0;
  margin: 0;
}

.config-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.drawer-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: linear-gradient(135deg, #1e3a5f 0%, #0d1b2a 100%);
}

.header-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #00d4aa 0%, #00a896 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.header-content h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.interface-info {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.interface-info .code {
  font-family: var(--font-mono);
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 8px;
}

.drawer-body {
  padding: 24px;
}

.config-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.btn-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
}

.empty-state {
  text-align: center;
  padding: 48px 24px;
  background: var(--color-bg-light);
  border-radius: 12px;
  border: 2px dashed var(--color-border);
}

.empty-state svg {
  width: 48px;
  height: 48px;
  color: var(--color-text-tertiary);
  margin-bottom: 16px;
}

.empty-state p {
  margin: 0 0 8px;
  font-size: 15px;
  color: var(--color-text-secondary);
}

.empty-state span {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.config-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-card {
  background: var(--color-bg-light);
  border-radius: 12px;
  border: 1px solid var(--color-border);
  overflow: hidden;
  transition: all 0.2s;
}

.config-card:hover {
  border-color: var(--color-primary-light);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.config-card.active {
  border-color: rgba(0, 212, 170, 0.4);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: rgba(0, 0, 0, 0.02);
  border-bottom: 1px solid var(--color-border);
}

.vendor-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.vendor-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-icon {
  width: 18px;
  height: 18px;
}

.card-body {
  padding: 16px 20px;
}

.config-item {
  margin-bottom: 12px;
}

.config-item:last-child {
  margin-bottom: 0;
}

.config-item .label {
  display: block;
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.config-item .value {
  font-size: 14px;
  color: var(--color-text-primary);
}

.config-item .value.url {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-primary);
  word-break: break-all;
}

.config-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.config-row .config-item {
  margin-bottom: 0;
}

.fallback-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(245, 158, 11, 0.1);
  border-radius: 8px;
  font-size: 13px;
  color: #f59e0b;
}

.fallback-info svg {
  width: 16px;
  height: 16px;
}

/* 表单样式 */
.config-form {
  padding: 0 24px;
}

.config-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.form-tip {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

.fallback-section {
  padding: 16px 0;
}

.section-divider {
  height: 1px;
  background: var(--color-border);
  margin: 24px 0;
}

.form-drawer :deep(.el-divider__text) {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .config-row {
    grid-template-columns: 1fr;
  }
}
</style>
