<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>调用记录</h2>
        <p class="header-desc">查看API调用明细与统计分析</p>
      </div>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <StatCard label="总调用次数" :value="statsData.totalCount" />
      </el-col>
      <el-col :span="6">
        <StatCard label="成功调用" :value="statsData.successCount" variant="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均耗时" :value="statsData.averageDurationMs" suffix="ms" variant="warning" />
      </el-col>
      <el-col :span="6">
        <StatCard label="总费用" :value="statsData.totalCost.toFixed(2)" prefix="¥" variant="info" />
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.apiCode" placeholder="接口编码" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-input v-model="searchForm.productCode" placeholder="产品编码" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-input v-model="searchForm.sceneCode" placeholder="场景编码" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.dataType" placeholder="数据类型" clearable class="search-select">
            <el-option v-for="item in dataTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-select v-model="searchForm.cacheHit" placeholder="缓存" clearable class="search-select">
            <el-option label="缓存命中" value="true" />
            <el-option label="实时调用" value="false" />
          </el-select>
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="date-picker"
          />
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport">导出</el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="callerId" label="调用方ID" width="100" />
        <el-table-column prop="apiCode" label="接口编码" width="150" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品" width="140" show-overflow-tooltip />
        <el-table-column prop="sceneCode" label="场景" width="150" show-overflow-tooltip />
        <el-table-column prop="vendorCode" label="厂商" width="120" />
        <el-table-column prop="dataType" label="数据类型" width="120" />
        <el-table-column prop="callTime" label="调用时间" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ row.callTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="100">
          <template #default="{ row }">
            <span :class="['response-time', { slow: (row.durationMs || row.latency || 0) > 1000 }]">{{ row.durationMs || row.latency || 0 }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="success" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cacheHit" label="缓存" width="100">
          <template #default="{ row }">
            <el-tag :type="row.cacheHit ? 'info' : 'warning'" size="small">{{ row.cacheHit ? '命中' : '实时' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="费用" width="100">
          <template #default="{ row }">
            <span class="cost-cell">¥{{ row.cost?.toFixed(2) || '0.00' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requestId" label="请求ID" width="180">
          <template #default="{ row }">
            <span class="trace-id">{{ row.requestId }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCallRecordList, getCallDimensionStats } from '@/api/call'
import type { CallRecord } from '@/types'
import { useCacheStore } from '@/stores/cache'
import { extractPageData } from '@/utils/pagination'
// StatCard is globally registered by unplugin-vue-components

const loading = ref(false)
const tableData = ref<CallRecord[]>([])
const total = ref(0)
const searchForm = reactive({
  apiCode: '',
  productCode: '',
  sceneCode: '',
  dataType: '',
  status: '',
  cacheHit: '',
  dateRange: [] as string[]
})
const pagination = reactive({
  currentPage: 1,
  pageSize: 10
})

const cacheStore = useCacheStore()
const dataTypeOptions = computed(() =>
  cacheStore.dataTypeOptions.map(dt => ({
    label: dt.dataTypeName,
    value: dt.dataTypeCode
  }))
)

const statsData = ref({
  totalCount: 0,
  successCount: 0,
  averageDurationMs: 0,
  totalCost: 0
})

const fetchStats = async () => {
  try {
    const res = await getCallDimensionStats(buildQueryParams())
    if (res.data) {
      statsData.value = {
        totalCount: res.data.totalCount || 0,
        successCount: res.data.successCount || 0,
        averageDurationMs: Math.round(res.data.averageDurationMs || 0),
        totalCost: Number(res.data.totalCost || 0)
      }
    }
  } catch {
    // Keep default values on error
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getCallRecordList({
      page: pagination.currentPage,
      pageSize: pagination.pageSize,
      ...buildQueryParams()
    })
    const { list, total: totalCount } = extractPageData<CallRecord>(res)
    tableData.value = list
    total.value = totalCount
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.currentPage = 1
  fetchStats()
  fetchList()
}

const handleReset = () => {
  searchForm.apiCode = ''
  searchForm.productCode = ''
  searchForm.sceneCode = ''
  searchForm.dataType = ''
  searchForm.status = ''
  searchForm.cacheHit = ''
  searchForm.dateRange = []
  pagination.currentPage = 1
  fetchStats()
  fetchList()
}

const handleExport = () => {
  ElMessage.info('导出功能开发中...')
}

const buildQueryParams = () => {
  const params: Record<string, any> = {}
  if (searchForm.apiCode) params.apiCode = searchForm.apiCode
  if (searchForm.productCode) params.productCode = searchForm.productCode
  if (searchForm.sceneCode) params.sceneCode = searchForm.sceneCode
  if (searchForm.dataType) params.dataType = searchForm.dataType
  if (searchForm.status) params.success = searchForm.status === 'success'
  if (searchForm.cacheHit) params.cacheHit = searchForm.cacheHit === 'true'
  if (searchForm.dateRange?.length === 2) {
    params.startTime = searchForm.dateRange[0]
    params.endTime = searchForm.dateRange[1]
  }
  return params
}

onMounted(async () => {
  await Promise.all([
    cacheStore.loadDataTypes(),
    fetchStats()
  ])
  fetchList()
})
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }

.stats-row { margin-bottom: 20px; }

.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 160px; }
.search-select { width: 140px; }
.date-picker { width: 320px; }
.search-btn-group { display: flex; gap: 10px; }

.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.response-time { font-family: var(--font-mono); font-size: 13px; }
.response-time.slow { color: #F56C6C; }
.cost-cell { color: var(--color-primary); font-weight: 500; font-family: var(--font-mono); }
.trace-id { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 2px 8px; border-radius: 4px; }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
