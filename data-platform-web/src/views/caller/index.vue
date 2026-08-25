<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>内部系统管理</h2>
        <p class="header-desc">管理内部系统及其 API Key</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增内部系统
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.keyword" placeholder="搜索内部系统名称/编码" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="callerCode" label="系统编码" width="140">
          <template #default="{ row }">
            <span class="code-tag">{{ row.callerCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="callerName" label="系统名称" min-width="160" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleProducts(row)">产品</el-button>
            <el-button type="primary" link @click="handleApiKey(row)">API Key</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="callerDialogVisible" :title="callerForm.id ? '编辑内部系统' : '新增内部系统'" width="520px">
      <el-form :model="callerForm" label-width="100px">
        <el-form-item label="系统编码" required>
          <el-input v-model="callerForm.callerCode" :disabled="Boolean(callerForm.id)" />
        </el-form-item>
        <el-form-item label="系统名称" required>
          <el-input v-model="callerForm.callerName" />
        </el-form-item>
        <el-form-item label="系统类型">
          <el-input v-model="callerForm.callerType" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="callerForm.contactPerson" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="callerForm.contactPhone" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="callerForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="callerForm.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveCaller">保存</el-button>
      </template>
    </el-dialog>

    <!-- API Key弹窗 -->
    <el-dialog v-model="apiKeyVisible" title="API Key管理" width="820px" class="form-dialog">
      <div class="api-key-header">
        <el-button type="primary" @click="handleOpenCreateApiKey">创建API Key</el-button>
      </div>
      <el-table :data="apiKeyList" stripe class="api-key-table">
        <el-table-column prop="keyName" label="名称" min-width="120" />
        <el-table-column prop="apiKey" label="API Key" min-width="230">
          <template #default="{ row }">
            <code class="api-key-value">{{ row.apiKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimit" label="速率限制" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rateLimitEnabled === false" type="info" size="small">不限流</el-tag>
            <span v-else>{{ row.rateLimit ?? 100 }}/min</span>
          </template>
        </el-table-column>
        <el-table-column prop="quotaUsed" label="已用/配额" width="120" align="center">
          <template #default="{ row }">{{ row.quotaUsed || 0 }} / {{ row.quotaLimit || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === COMMON_STATUS.ACTIVE ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleInterfaceAuth(row.id!)">申请接口权限</el-button>
            <el-button type="primary" link @click="handleProductAuth(row.id!)">产品授权</el-button>
            <el-button type="primary" link @click="handleRateLimitConfig(row)">限流配置</el-button>
            <el-button type="danger" link @click="handleDeleteApiKey(row.id!)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="apiKeyCreateVisible" title="创建 API Key" width="560px">
      <el-form :model="apiKeyCreateForm" label-width="100px">
        <el-form-item label="Key 名称" required>
          <el-input v-model="apiKeyCreateForm.name" maxlength="100" placeholder="例如：生产环境调用" />
        </el-form-item>
        <el-form-item label="授权产品" required>
          <div class="product-select-field">
            <el-select
              v-model="apiKeyCreateForm.productIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择该 Key 可调用的产品"
              class="product-select"
            >
              <el-option
                v-for="product in activeProductList"
                :key="product.id"
                :label="`${product.productName} (${product.productCode})`"
                :value="product.id!"
              />
            </el-select>
            <div class="product-select-hint">
              API Key 创建后，只能调用这里授权的产品。
              <el-button link type="primary" @click="handleOpenProductFromApiKey">添加产品</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="apiKeyCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateApiKey">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rateLimitDialogVisible" title="API Key限流配置" width="480px">
      <el-form :model="rateLimitForm" label-width="140px">
        <el-form-item label="启用限流">
          <el-switch v-model="rateLimitForm.rateLimitEnabled" />
        </el-form-item>
        <el-form-item label="每分钟最大请求数" required>
          <el-input-number
            v-model="rateLimitForm.rateLimit"
            :min="1"
            :max="1000000"
            :step="10"
            :disabled="!rateLimitForm.rateLimitEnabled"
            controls-position="right"
            class="rate-limit-input"
          />
        </el-form-item>
        <div class="policy-hint">
          关闭限流后，该 API Key 不再执行每分钟请求数检查；重新开启时继续使用已保存的请求上限。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="rateLimitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveRateLimit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 产品授权弹窗 -->
    <el-dialog v-model="productAuthVisible" title="API Key产品授权" width="800px" class="config-dialog">
      <div class="config-container">
        <el-transfer
          v-model="selectedProducts"
          :data="productTransferList"
          :titles="['可选产品', '已授权产品']"
          :props="{ key: 'id', label: 'transferLabel' }"
          filterable
          filter-placeholder="搜索产品名称或编码"
          class="custom-transfer"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button size="large" @click="productAuthVisible = false">取消</el-button>
          <el-button type="primary" size="large" :loading="submitting" @click="handleSaveProductAuth">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCallerList,
  createCaller,
  deleteCaller,
  getApiKeyList,
  createApiKey,
  updateApiKeyRateLimit,
  deleteApiKey,
  updateCallerStatus,
  getCallerProducts,
  getApiKeyProducts,
  assignApiKeyProducts,
  updateCaller
} from '@/api/caller'
import type { Caller, ApiKey, CallerProduct } from '@/api/caller'
import { COMMON_STATUS } from '@/constants'

const router = useRouter()

const searchForm = reactive({ keyword: '', status: '' })
const tableData = ref<Caller[]>([])
const loading = ref(false)
const submitting = ref(false)
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const apiKeyVisible = ref(false)
const apiKeyCreateVisible = ref(false)
const rateLimitDialogVisible = ref(false)
const currentCallerId = ref<number>(0)
const apiKeyList = ref<ApiKey[]>([])
const productList = ref<CallerProduct[]>([])
const activeProductList = computed(() => productList.value.filter(product => product.id && product.status === COMMON_STATUS.ACTIVE))
const productTransferList = computed(() => activeProductList.value.map(product => ({
  ...product,
  transferLabel: `${product.productName} (${product.productCode})`
})))
const apiKeyCreateForm = reactive({
  name: '',
  productIds: [] as number[]
})
const productAuthVisible = ref(false)
const selectedProducts = ref<number[]>([])
const currentApiKeyId = ref<number | null>(null)
const rateLimitForm = reactive({
  rateLimitEnabled: true,
  rateLimit: 100
})
const callerDialogVisible = ref(false)
const callerForm = reactive<Caller>({
  callerCode: '',
  callerName: '',
  callerType: '',
  description: '',
  contactPerson: '',
  contactPhone: '',
  status: 'active'
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCallerList({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status as 'active' | 'inactive' | undefined
    })
    tableData.value = res.data || []
    pagination.total = res.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { searchForm.keyword = ''; searchForm.status = ''; loadData() }
const handleAdd = () => {
  Object.assign(callerForm, { id: undefined, callerCode: '', callerName: '', callerType: '', description: '', contactPerson: '', contactPhone: '', status: 'active' })
  callerDialogVisible.value = true
}
const handleEdit = (row: Caller) => {
  Object.assign(callerForm, { ...row })
  callerDialogVisible.value = true
}
const handleSaveCaller = async () => {
  if (!callerForm.callerCode.trim() || !callerForm.callerName.trim()) {
    ElMessage.warning('请填写调用方编码和名称')
    return
  }
  submitting.value = true
  try {
    if (callerForm.id) {
      await updateCaller(callerForm.id, { ...callerForm })
    } else {
      await createCaller({ ...callerForm })
    }
    ElMessage.success('保存成功')
    callerDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}
const handleDelete = async (row: Caller) => { await ElMessageBox.confirm(`确认删除"${row.callerName}"?`, '提示', { type: 'warning' }); await deleteCaller(row.id!); ElMessage.success('删除成功'); loadData() }
const handleProducts = async (row: Caller) => {
  await router.push(`/caller/${row.id}/products`)
}
const handleApiKey = async (row: Caller) => {
  currentCallerId.value = row.id!
  const [keyRes, productRes] = await Promise.all([getApiKeyList(row.id!), getCallerProducts(row.id!)])
  apiKeyList.value = (keyRes.data || []).filter(key => key.callerId === row.id)
  productList.value = productRes.data || []
  apiKeyVisible.value = true
}
const handleOpenCreateApiKey = () => {
  apiKeyCreateForm.name = ''
  apiKeyCreateForm.productIds = []
  apiKeyCreateVisible.value = true
}
const handleOpenProductFromApiKey = async () => {
  apiKeyCreateVisible.value = false
  apiKeyVisible.value = false
  await router.push(`/caller/${currentCallerId.value}/products`)
}
const handleCreateApiKey = async () => {
  if (!apiKeyCreateForm.name.trim()) {
    ElMessage.warning('请填写 Key 名称')
    return
  }
  if (apiKeyCreateForm.productIds.length === 0) {
    ElMessage.warning('请至少选择一个授权产品')
    return
  }
  submitting.value = true
  try {
    const res = await createApiKey({
      callerId: currentCallerId.value,
      name: apiKeyCreateForm.name.trim(),
      productIds: apiKeyCreateForm.productIds
    })
    const apiKey = res.data
    ElMessage.success('API Key及产品授权创建成功')
    apiKeyCreateVisible.value = false
    if (apiKey) apiKeyList.value = [...apiKeyList.value, apiKey]
  } finally {
    submitting.value = false
  }
}
const handleRateLimitConfig = (apiKey: ApiKey) => {
  currentApiKeyId.value = apiKey.id!
  rateLimitForm.rateLimitEnabled = apiKey.rateLimitEnabled !== false
  rateLimitForm.rateLimit = apiKey.rateLimit ?? 100
  rateLimitDialogVisible.value = true
}
const handleSaveRateLimit = async () => {
  if (!currentApiKeyId.value) return
  if (!Number.isInteger(rateLimitForm.rateLimit)
      || rateLimitForm.rateLimit < 1
      || rateLimitForm.rateLimit > 1000000) {
    ElMessage.warning('每分钟最大请求数必须是1到1000000之间的整数')
    return
  }
  submitting.value = true
  try {
    const response = await updateApiKeyRateLimit(currentApiKeyId.value, {
      rateLimitEnabled: rateLimitForm.rateLimitEnabled,
      rateLimit: rateLimitForm.rateLimit
    })
    const updated = response.data
    apiKeyList.value = apiKeyList.value.map(apiKey => apiKey.id === currentApiKeyId.value
      ? { ...apiKey, ...updated }
      : apiKey)
    ElMessage.success('限流策略保存成功')
    rateLimitDialogVisible.value = false
  } finally {
    submitting.value = false
  }
}
const handleDeleteApiKey = async (id: number) => { await deleteApiKey(id); ElMessage.success('删除成功'); apiKeyList.value = apiKeyList.value.filter(k => k.id !== id) }
const handleStatusChange = async (row: Caller) => {
  try {
    await updateCallerStatus(row.id!, row.status as typeof COMMON_STATUS.ACTIVE | typeof COMMON_STATUS.INACTIVE)
    ElMessage.success(row.status === COMMON_STATUS.ACTIVE ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === COMMON_STATUS.ACTIVE ? COMMON_STATUS.INACTIVE : COMMON_STATUS.ACTIVE
    ElMessage.error('状态更新失败')
  }
}

const handleProductAuth = async (apiKeyId: number) => {
  currentApiKeyId.value = apiKeyId
  try {
    const [productsRes, apiKeyProductsRes] = await Promise.all([
      getCallerProducts(currentCallerId.value),
      getApiKeyProducts(apiKeyId)
    ])
    productList.value = (productsRes.data || []).filter(product => product.status === COMMON_STATUS.ACTIVE)
    selectedProducts.value = apiKeyProductsRes.data || []
    productAuthVisible.value = true
  } catch (error) {
    ElMessage.error('加载产品授权数据失败')
  }
}

const handleSaveProductAuth = async () => {
  if (!currentApiKeyId.value) return
  submitting.value = true
  try {
    await assignApiKeyProducts(currentApiKeyId.value, selectedProducts.value)
    ElMessage.success('产品授权成功')
    productAuthVisible.value = false
  } catch (error) {
    ElMessage.error('产品授权失败')
  } finally {
    submitting.value = false
  }
}

const handleInterfaceAuth = async (apiKeyId: number) => {
  await router.push({
    path: '/api-permission',
    query: {
      create: '1',
      callerId: String(currentCallerId.value),
      apiKeyId: String(apiKeyId)
    }
  })
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.page-header .el-button svg { width: 18px; height: 18px; }
.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; }
.search-input { width: 280px; }
.search-select { width: 160px; }
.search-btn-group { display: flex; gap: 10px; }
.code-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 10px; border-radius: 6px; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
.api-key-header { margin-bottom: 16px; }
.api-key-value { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 8px; border-radius: 4px; word-break: break-all; }
.product-select-field { width: 100%; }
.product-select { width: 100%; }
.product-select-hint { margin-top: 8px; color: var(--color-text-tertiary); font-size: 12px; line-height: 1.5; }
.rate-limit-input { width: 100%; }
.policy-hint { margin-left: 140px; color: var(--color-text-tertiary); font-size: 12px; line-height: 1.6; }
.config-dialog :deep(.el-dialog__header) { padding: 24px 24px 16px; border-bottom: 1px solid var(--color-border); }
.config-dialog :deep(.el-dialog__title) { font-size: 20px; font-weight: 600; color: var(--color-text-primary); }
.config-container { padding: 8px 0; }
.custom-transfer { --el-transfer-panel-width: 300px; display: flex; align-items: center; justify-content: center; }
.custom-transfer :deep(.el-transfer-panel) { border-radius: 12px; overflow: hidden; box-shadow: var(--shadow-sm); }
.custom-transfer :deep(.el-transfer-panel__header) { background: linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-primary) 100%); padding: 16px 20px; }
.custom-transfer :deep(.el-transfer-panel__header .el-checkbox__label) { color: white; font-weight: 600; font-size: 14px; }
.custom-transfer :deep(.el-transfer-panel__header .el-checkbox__inner) { border-color: white; }
.custom-transfer :deep(.el-transfer-panel__body) { height: 400px; }
.custom-transfer :deep(.el-transfer-panel__item) { border-radius: 8px; }
.custom-transfer :deep(.el-transfer__buttons) { padding: 0 24px; }
.custom-transfer :deep(.el-transfer__button) { border-radius: 10px; width: 44px; height: 44px; }
.dialog-footer { display: flex; gap: 12px; justify-content: flex-end; }
</style>
