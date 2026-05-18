<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>数据查询测试</h2>
        <p class="header-desc">测试数据接口调用，验证接口配置正确性</p>
      </div>
    </div>

    <!-- 查询配置区域 -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span>查询配置</span>
        </div>
      </template>

      <!-- 三级联动选择器 -->
      <div class="selector-row">
        <div class="selector-item wide">
          <label class="selector-label">API Key</label>
          <el-input
            v-model="apiKey"
            placeholder="请输入外部调用 API Key"
            show-password
            class="selector-input"
          />
        </div>

        <div class="selector-item">
          <label class="selector-label">厂商</label>
          <el-select
            v-model="selectedVendorId"
            placeholder="请选择厂商"
            clearable
            class="selector-input"
            @change="handleVendorChange"
          >
            <el-option
              v-for="vendor in vendorList"
              :key="vendor.id"
              :label="vendor.vendorName"
              :value="vendor.id"
            />
          </el-select>
        </div>

        <div class="selector-item">
          <label class="selector-label">数据类型</label>
          <el-select
            v-model="selectedDataTypeId"
            placeholder="请选择数据类型"
            clearable
            :disabled="!selectedVendorId"
            class="selector-input"
            @change="handleDataTypeChange"
          >
            <el-option
              v-for="dt in filteredDataTypeList"
              :key="dt.id"
              :label="dt.dataTypeName"
              :value="dt.id"
            />
          </el-select>
        </div>

        <div class="selector-item">
          <label class="selector-label">接口</label>
          <el-select
            v-model="selectedInterfaceId"
            placeholder="请选择接口"
            clearable
            :disabled="!selectedDataTypeId"
            class="selector-input"
            @change="handleInterfaceChange"
          >
            <el-option
              v-for="intf in interfaceList"
              :key="intf.id"
              :label="intf.interfaceName"
              :value="intf.id"
            />
          </el-select>
        </div>
      </div>

      <div class="selector-row">
        <div class="selector-item">
          <label class="selector-label">调用方</label>
          <el-select
            v-model="selectedCallerId"
            placeholder="用于加载产品"
            clearable
            filterable
            class="selector-input"
            @change="handleCallerChange"
          >
            <el-option
              v-for="caller in callerList"
              :key="caller.id"
              :label="caller.callerName"
              :value="caller.id"
            />
          </el-select>
        </div>

        <div class="selector-item">
          <label class="selector-label">产品</label>
          <el-select
            v-model="productCode"
            placeholder="请选择或输入产品编码"
            clearable
            filterable
            allow-create
            class="selector-input"
          >
            <el-option
              v-for="product in productList"
              :key="product.id || product.productCode"
              :label="`${product.productName} (${product.productCode})`"
              :value="product.productCode"
            />
          </el-select>
        </div>

        <div class="selector-item">
          <label class="selector-label">场景</label>
          <el-select
            v-model="sceneCode"
            placeholder="请选择调用场景"
            clearable
            filterable
            class="selector-input"
          >
            <el-option
              v-for="scene in sceneList"
              :key="scene.id || scene.sceneCode"
              :label="`${scene.sceneName} (${scene.sceneCode})`"
              :value="scene.sceneCode"
            />
          </el-select>
        </div>

        <div class="selector-item cache-item">
          <label class="selector-label">历史缓存</label>
          <div class="cache-controls">
            <el-switch v-model="useCache" />
            <el-input-number
              v-model="cacheDays"
              :min="1"
              :max="30"
              :disabled="!useCache"
              controls-position="right"
              class="cache-days"
            />
          </div>
        </div>
      </div>

      <!-- JSON参数编辑器 -->
      <div class="json-editor-section">
        <div class="editor-header">
          <label class="selector-label">请求参数 (JSON)</label>
          <el-button size="small" @click="formatJson">格式化</el-button>
        </div>
        <el-input
          v-model="requestParams"
          type="textarea"
          :rows="8"
          placeholder='请输入JSON格式的请求参数，例如: {"name": "张三"}'
          class="json-textarea"
        />
        <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
      </div>

      <!-- 执行按钮 -->
      <div class="action-row">
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!canExecute"
          @click="handleExecute"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          执行查询
        </el-button>
        <el-button @click="handleClear">清空</el-button>
      </div>
    </el-card>

    <!-- 结果展示区域 -->
    <el-card v-if="hasResult" class="result-card">
      <template #header>
        <div class="card-header result-header">
          <span>查询结果</span>
          <div class="result-meta">
            <el-tag v-if="result?.cached" type="info" size="small">缓存命中</el-tag>
            <span class="latency">耗时: {{ latencyDisplay }}</span>
          </div>
        </div>
      </template>

      <!-- 成功结果 -->
      <div v-if="result?.success" class="result-content">
        <div class="result-status success">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          <span>查询成功</span>
        </div>
        <div class="result-summary">
          <span>请求ID: {{ result?.platformRequestId || result?.requestId || '-' }}</span>
          <span>产品: {{ result?.productCode || productCode || '-' }}</span>
          <span>场景: {{ result?.sceneCode || sceneCode || '-' }}</span>
          <span>费用: ¥{{ (result?.cost || 0).toFixed(2) }}</span>
        </div>
        <div class="json-viewer">
          <pre>{{ formattedResultData }}</pre>
        </div>
      </div>

      <!-- 失败结果 -->
      <div v-else class="result-content">
        <div class="result-status error">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
          <span>查询失败</span>
        </div>
        <div class="error-info">
          <p><strong>错误码:</strong> {{ result?.errorCode || '-' }}</p>
          <p><strong>错误信息:</strong> {{ result?.errorMsg || '-' }}</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getVendorList } from '@/api/vendor'
import { getDataTypeList } from '@/api/datatype'
import { getInterfaceOptions } from '@/api/interface'
import { executeOpenApiQuery } from '@/api/data-query'
import { getCallerList, getCallerProducts } from '@/api/caller'
import { getCallSceneList } from '@/api/call-scene'
import type { Vendor, DataType, ApiInterface, DataQueryResponse, CallerDTO, CallerProductDTO, CallSceneDTO } from '@/types'

type CallerOption = CallerDTO & { id: number }

// 选择器数据
const vendorList = ref<Vendor[]>([])
const dataTypeList = ref<DataType[]>([])
const interfaceList = ref<ApiInterface[]>([])
const vendorInterfaceOptions = ref<ApiInterface[]>([])
const callerList = ref<CallerOption[]>([])
const productList = ref<CallerProductDTO[]>([])
const sceneList = ref<CallSceneDTO[]>([])

// 选中值
const selectedVendorId = ref<number | null>(null)
const selectedDataTypeId = ref<number | null>(null)
const selectedInterfaceId = ref<number | null>(null)
const selectedCallerId = ref<number | null>(null)
const apiKey = ref('')
const productCode = ref('')
const sceneCode = ref('')
const useCache = ref(false)
const cacheDays = ref(3)

// 请求参数
const requestParams = ref('{\n  \n}')
const jsonError = ref('')

// 执行状态
const loading = ref(false)
const result = ref<DataQueryResponse | null>(null)

// 计算属性: 根据厂商可用接口反推数据类型
const filteredDataTypeList = computed(() => {
  if (!selectedVendorId.value) {
    return []
  }
  const dataTypeIds = new Set(vendorInterfaceOptions.value.map(item => item.dataTypeId))
  return dataTypeList.value.filter(item => item.id && dataTypeIds.has(item.id))
})

// 是否可以执行查询
const canExecute = computed(() => {
  return Boolean(apiKey.value.trim() && selectedInterfaceId.value && productCode.value.trim() && sceneCode.value)
})

// 是否有结果
const hasResult = computed(() => {
  return result.value !== null
})

// 延迟显示
const latencyDisplay = computed(() => {
  if (result.value?.latency !== undefined) {
    return `${result.value.latency} ms`
  }
  return '-'
})

// 格式化结果数据
const formattedResultData = computed(() => {
  if (result.value?.data) {
    try {
      return JSON.stringify(result.value.data, null, 2)
    } catch {
      return String(result.value.data)
    }
  }
  return ''
})

// 加载厂商列表
const loadVendors = async () => {
  try {
    const res = await getVendorList({ page: 1, pageSize: 1000, status: 'active' })
    vendorList.value = res.data || []
  } catch (error) {
    console.error('加载厂商列表失败:', error)
  }
}

// 加载数据类型列表
const loadDataTypes = async () => {
  try {
    const res = await getDataTypeList({ page: 1, pageSize: 1000, status: 'active' })
    dataTypeList.value = res.data || []
  } catch (error) {
    console.error('加载数据类型列表失败:', error)
  }
}

const loadCallers = async () => {
  try {
    const res = await getCallerList({ page: 1, pageSize: 1000, status: 'active' })
    callerList.value = (res.data || []).filter((caller): caller is CallerOption => caller.id !== undefined)
  } catch (error) {
    console.error('加载调用方列表失败:', error)
  }
}

const loadScenes = async () => {
  try {
    const res = await getCallSceneList()
    sceneList.value = (res.data || []).filter(scene => scene.status === 'active')
  } catch (error) {
    console.error('加载场景列表失败:', error)
  }
}

// 厂商变更处理
const handleVendorChange = async () => {
  selectedDataTypeId.value = null
  selectedInterfaceId.value = null
  interfaceList.value = []
  vendorInterfaceOptions.value = []
  result.value = null

  if (!selectedVendorId.value) return
  try {
    const res = await getInterfaceOptions({ vendorId: selectedVendorId.value, status: 'active' })
    vendorInterfaceOptions.value = res.data || []
  } catch (error) {
    console.error('加载厂商接口选项失败:', error)
  }
}

// 数据类型变更处理
const handleDataTypeChange = async () => {
  selectedInterfaceId.value = null
  result.value = null

  if (selectedDataTypeId.value) {
    try {
      const res = await getInterfaceOptions({
        vendorId: selectedVendorId.value || undefined,
        dataTypeId: selectedDataTypeId.value,
        status: 'active'
      })
      interfaceList.value = res.data || []
    } catch (error) {
      console.error('加载接口列表失败:', error)
      interfaceList.value = []
    }
  } else {
    interfaceList.value = []
  }
}

// 接口变更处理
const handleInterfaceChange = () => {
  result.value = null
}

const handleCallerChange = async () => {
  productCode.value = ''
  productList.value = []
  result.value = null
  if (!selectedCallerId.value) return
  try {
    const res = await getCallerProducts(selectedCallerId.value)
    productList.value = (res.data || []).filter(product => product.status === 'active')
  } catch (error) {
    console.error('加载产品列表失败:', error)
  }
}

// 格式化JSON
const formatJson = () => {
  try {
    const parsed = JSON.parse(requestParams.value)
    requestParams.value = JSON.stringify(parsed, null, 2)
    jsonError.value = ''
  } catch (e) {
    jsonError.value = 'JSON格式错误，请检查输入'
  }
}

// 执行查询
const handleExecute = async () => {
  // 验证JSON
  let params: Record<string, any> = {}
  try {
    params = JSON.parse(requestParams.value || '{}')
    jsonError.value = ''
  } catch {
    jsonError.value = 'JSON格式错误，请检查输入'
    return
  }

  // 获取选中的对象
  const dataType = dataTypeList.value.find(dt => dt.id === selectedDataTypeId.value)
  const intf = interfaceList.value.find(i => i.id === selectedInterfaceId.value)

  if (!dataType || !intf) {
    ElMessage.warning('请选择数据类型和接口')
    return
  }
  if (!apiKey.value.trim() || !productCode.value.trim() || !sceneCode.value) {
    ElMessage.warning('请填写 API Key、产品和场景')
    return
  }

  loading.value = true
  result.value = null

  try {
    const res = await executeOpenApiQuery(apiKey.value.trim(), {
      requestId: `web-${Date.now()}`,
      apiCode: intf.interfaceCode,
      productCode: productCode.value.trim(),
      sceneCode: sceneCode.value,
      useCache: useCache.value,
      cacheDays: useCache.value ? cacheDays.value : undefined,
      params
    })
    result.value = res

    if (res.success) {
      ElMessage.success('查询成功')
    } else {
      ElMessage.error(res.errorMsg || '查询失败')
    }
  } catch (error: any) {
    console.error('查询失败:', error)
    result.value = {
      success: false,
      errorCode: 'NETWORK_ERROR',
      errorMsg: error.message || '网络请求失败'
    }
    ElMessage.error('查询请求失败')
  } finally {
    loading.value = false
  }
}

// 清空
const handleClear = () => {
  selectedVendorId.value = null
  selectedDataTypeId.value = null
  selectedInterfaceId.value = null
  selectedCallerId.value = null
  apiKey.value = ''
  productCode.value = ''
  sceneCode.value = ''
  useCache.value = false
  cacheDays.value = 3
  requestParams.value = '{\n  \n}'
  jsonError.value = ''
  result.value = null
  interfaceList.value = []
  vendorInterfaceOptions.value = []
  productList.value = []
}

onMounted(() => {
  loadVendors()
  loadDataTypes()
  loadCallers()
  loadScenes()
})
</script>

<style scoped>
.page-container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.header-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}

/* 配置卡片 */
.config-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: 600;
  color: var(--color-text-primary);
}

/* 选择器行 */
.selector-row {
  display: flex;
  gap: 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.selector-item {
  flex: 1;
  min-width: 200px;
}

.selector-item.wide {
  flex: 2;
  min-width: 320px;
}

.selector-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.selector-input {
  width: 100%;
}

.cache-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}

.cache-days {
  width: 112px;
}

/* JSON编辑器 */
.json-editor-section {
  margin-bottom: 20px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.json-textarea :deep(textarea) {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}

.json-error {
  margin-top: 8px;
  color: var(--el-color-danger);
  font-size: 12px;
}

/* 操作行 */
.action-row {
  display: flex;
  gap: 12px;
}

.action-row .el-button {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

/* 结果卡片 */
.result-card {
  margin-bottom: 20px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.latency {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 结果内容 */
.result-content {
  min-height: 200px;
}

.result-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  font-weight: 500;
}

.result-status svg {
  width: 20px;
  height: 20px;
}

.result-status.success {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.result-status.error {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.result-summary span {
  background: var(--color-bg-light);
  border-radius: 6px;
  padding: 6px 10px;
}

/* JSON查看器 */
.json-viewer {
  background: var(--color-bg-light);
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
}

.json-viewer pre {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

/* 错误信息 */
.error-info {
  background: var(--color-bg-light);
  border-radius: 8px;
  padding: 16px;
}

.error-info p {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--color-text-primary);
}

.error-info p:last-child {
  margin-bottom: 0;
}

.error-info strong {
  color: var(--color-text-secondary);
}

/* 响应式 */
@media (max-width: 768px) {
  .selector-row {
    flex-direction: column;
  }

  .selector-item {
    min-width: 100%;
  }
}
</style>
