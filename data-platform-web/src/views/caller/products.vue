<template>
  <div class="page-container">
    <div class="page-header">
      <div class="title-group">
        <el-button link type="primary" class="back-button" @click="handleBack">← 返回内部系统管理</el-button>
        <h2>产品管理</h2>
        <p class="header-desc">
          {{ caller ? `${caller.callerName}（${caller.callerCode}）` : '加载内部系统信息中…' }}
        </p>
      </div>
      <el-button type="primary" :disabled="!caller" @click="handleAdd">新增产品</el-button>
    </div>

    <el-card class="system-card" shadow="never">
      <div class="system-summary">
        <div>
          <span class="summary-label">内部系统</span>
          <strong>{{ caller?.callerName || '-' }}</strong>
        </div>
        <div>
          <span class="summary-label">系统编码</span>
          <span class="code-tag">{{ caller?.callerCode || '-' }}</span>
        </div>
        <div>
          <span class="summary-label">产品数量</span>
          <strong>{{ products.length }}</strong>
        </div>
        <div>
          <span class="summary-label">系统状态</span>
          <el-tag :type="caller?.status === COMMON_STATUS.ACTIVE ? 'success' : 'danger'" size="small">
            {{ caller?.status === COMMON_STATUS.ACTIVE ? '启用' : '禁用' }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-card class="search-card" shadow="never">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索产品名称/编码"
            clearable
            class="search-input"
          />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" :value="COMMON_STATUS.ACTIVE" />
            <el-option label="禁用" :value="COMMON_STATUS.INACTIVE" />
          </el-select>
          <el-select v-model="searchForm.cacheScope" placeholder="复用条件" clearable class="search-select">
            <el-option label="调用方内复用" value="CALLER" />
            <el-option label="全局复用" value="GLOBAL" />
          </el-select>
        </div>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="filteredProducts" v-loading="loading" stripe>
        <el-table-column prop="productCode" label="产品编码" min-width="170">
          <template #default="{ row }">
            <span class="code-tag">{{ row.productCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="200" />
        <el-table-column prop="cacheScope" label="复用条件" width="160">
          <template #default="{ row }">
            <el-tag :type="row.cacheScope === 'CALLER' ? 'warning' : 'info'" size="small">
              {{ cacheScopeLabel(row.cacheScope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === COMMON_STATUS.ACTIVE ? 'success' : 'danger'" size="small">
              {{ row.status === COMMON_STATUS.ACTIVE ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无产品，点击右上角新增产品" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="productForm.id ? '编辑产品' : '新增产品'" width="560px">
      <el-form :model="productForm" label-width="110px">
        <el-form-item label="产品编码" required>
          <el-input
            v-model="productForm.productCode"
            :disabled="Boolean(productForm.id)"
            maxlength="64"
            placeholder="例如：loan-risk"
          />
          <div v-if="productForm.id" class="field-hint">产品编码用于调用匹配，创建后不可修改。</div>
        </el-form-item>
        <el-form-item label="产品名称" required>
          <el-input v-model="productForm.productName" maxlength="100" placeholder="例如：信贷风控" />
        </el-form-item>
        <el-form-item label="复用条件" required>
          <el-radio-group v-model="productForm.cacheScope" class="scope-options">
            <el-radio-button value="CALLER">调用方内复用</el-radio-button>
            <el-radio-button value="GLOBAL">全局复用</el-radio-button>
          </el-radio-group>
          <div class="field-hint">
            {{ productForm.cacheScope === 'CALLER'
              ? '仅复用当前内部系统产生的缓存结果。'
              : '允许复用符合调用条件的全局缓存结果。' }}
          </div>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-radio-group v-model="productForm.status">
            <el-radio-button :value="COMMON_STATUS.ACTIVE">启用</el-radio-button>
            <el-radio-button :value="COMMON_STATUS.INACTIVE">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createCallerProduct,
  getCaller,
  getCallerProducts,
  updateCallerProduct
} from '@/api/caller'
import type { Caller, CallerProduct } from '@/api/caller'
import { COMMON_STATUS } from '@/constants'

const route = useRoute()
const router = useRouter()
const callerId = Number(route.params.callerId)

const caller = ref<Caller | null>(null)
const products = ref<CallerProduct[]>([])
const loading = ref(false)
const submitting = ref(false)
const formVisible = ref(false)
const searchForm = reactive({
  keyword: '',
  status: '',
  cacheScope: ''
})
const productForm = reactive<CallerProduct>({
  productCode: '',
  productName: '',
  cacheScope: 'CALLER',
  status: COMMON_STATUS.ACTIVE
})

const filteredProducts = computed(() => {
  const keyword = searchForm.keyword.trim().toLowerCase()
  return products.value.filter(product => {
    const matchesKeyword = !keyword
      || product.productCode.toLowerCase().includes(keyword)
      || product.productName.toLowerCase().includes(keyword)
    const matchesStatus = !searchForm.status || product.status === searchForm.status
    const matchesScope = !searchForm.cacheScope || product.cacheScope === searchForm.cacheScope
    return matchesKeyword && matchesStatus && matchesScope
  })
})

const loadPage = async () => {
  if (!Number.isInteger(callerId) || callerId <= 0) {
    ElMessage.error('内部系统参数无效')
    await router.replace('/caller')
    return
  }
  loading.value = true
  try {
    const [callerRes, productRes] = await Promise.all([
      getCaller(callerId),
      getCallerProducts(callerId)
    ])
    caller.value = callerRes.data || null
    products.value = productRes.data || []
  } finally {
    loading.value = false
  }
}

const resetProductForm = () => {
  Object.assign(productForm, {
    id: undefined,
    callerId,
    productCode: '',
    productName: '',
    cacheScope: 'CALLER',
    status: COMMON_STATUS.ACTIVE
  })
}

const handleAdd = () => {
  resetProductForm()
  formVisible.value = true
}

const handleEdit = (product: CallerProduct) => {
  Object.assign(productForm, {
    ...product,
    cacheScope: product.cacheScope || 'CALLER',
    status: product.status || COMMON_STATUS.ACTIVE
  })
  formVisible.value = true
}

const handleSubmit = async () => {
  if (!productForm.productCode.trim() || !productForm.productName.trim()) {
    ElMessage.warning('请填写产品编码和产品名称')
    return
  }
  submitting.value = true
  try {
    const payload: CallerProduct = {
      productCode: productForm.productCode.trim(),
      productName: productForm.productName.trim(),
      cacheScope: productForm.cacheScope,
      status: productForm.status
    }
    if (productForm.id) {
      await updateCallerProduct(callerId, productForm.id, payload)
      ElMessage.success('产品配置已更新')
    } else {
      await createCallerProduct(callerId, payload)
      ElMessage.success('产品添加成功')
    }
    formVisible.value = false
    const response = await getCallerProducts(callerId)
    products.value = response.data || []
  } finally {
    submitting.value = false
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.cacheScope = ''
}

const cacheScopeLabel = (scope?: string) => scope === 'GLOBAL' ? '全局复用' : '调用方内复用'
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const handleBack = () => router.push('/caller')

onMounted(loadPage)
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 24px; }
.title-group { min-width: 0; }
.back-button { margin: 0 0 10px -4px; }
.page-header h2 { margin: 0 0 4px; color: var(--color-text-primary); font-size: 24px; font-weight: 700; }
.header-desc { margin: 0; color: var(--color-text-tertiary); font-size: 14px; }
.system-card, .search-card { margin-bottom: 20px; }
.system-summary { display: grid; grid-template-columns: minmax(180px, 2fr) repeat(3, minmax(130px, 1fr)); gap: 24px; align-items: center; }
.system-summary > div { display: flex; flex-direction: column; gap: 8px; }
.summary-label { color: var(--color-text-tertiary); font-size: 12px; }
.search-bar { display: flex; justify-content: space-between; gap: 16px; }
.search-inputs { display: flex; gap: 12px; flex: 1; }
.search-input { width: 300px; }
.search-select { width: 170px; }
.code-tag { width: fit-content; padding: 4px 10px; border-radius: 6px; background: var(--color-bg-light); color: var(--color-text-secondary); font-family: var(--font-mono); font-size: 13px; }
.field-hint { margin-top: 7px; color: var(--color-text-tertiary); font-size: 12px; line-height: 1.5; }
.scope-options { width: 100%; }
@media (max-width: 900px) {
  .page-header { align-items: flex-start; gap: 16px; }
  .system-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .search-bar, .search-inputs { flex-direction: column; }
  .search-input, .search-select { width: 100%; }
}
</style>
